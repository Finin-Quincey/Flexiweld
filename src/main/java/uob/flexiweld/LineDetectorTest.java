package uob.flexiweld;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LineDetectorTest {

	public static final int CAMERA_NUMBER = 0;
	public static final String WINDOW_NAME = "Line Detection Test";

	public static final int FRAMERATE = 30; // Target framerate in frames per second

	public static final double ANGLE_THRESHOLD = Math.toRadians(10); // Lines within 2 degrees are considered parallel
	public static final double DISTANCE_THRESHOLD = 10;

	public static final int BORDER = 20;

	public static final Scalar WHITE = new Scalar(255, 255, 255); // This is blue-green-red for some reason
	public static final Scalar GREEN = new Scalar(0, 255, 0);
	public static final Scalar CYAN = new Scalar(255, 255, 0);
	public static final Scalar YELLOW = new Scalar(0, 255, 255);

	public static final int INTERP_FRAMES = 5; // Number of frames to average over for tube detection

	private static final List<List<Line>> prevLines = new ArrayList<>();

	public static void main(String[] args){

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
//		Mat mat = Mat.eye(3, 3, CvType.CV_8UC1);
//		System.out.println("mat = " + mat.dump());

		VideoCapture vc = new VideoCapture();
		vc.open(CAMERA_NUMBER);
		System.out.println(vc.isOpened() ? "Video capture opened successfully" : "Failed to open video capture");

		Mat frame = new Mat();
		Mat edges;

		vc.read(frame);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		float scaleFactor = Math.min(screenSize.width/frame.width(), screenSize.height/frame.height());
		int newWidth = (int)(frame.width() * scaleFactor);
		int newHeight = (int)(frame.height() * scaleFactor);
		final Size size = new Size(newWidth, newHeight);

		final Mat mapX = new Mat(size, CvType.CV_32F);
		final Mat mapY = new Mat(size, CvType.CV_32F);

		final Point textPt0 = new Point(10, 30);
		final Point textPt1 = new Point(10, 100);
		final Point textPt2 = new Point(10, newHeight - 90);
		final Point textPt3 = new Point(10, newHeight - 60);
		final Point textPt4 = new Point(10, newHeight - 30);

		Utils.setupFlipMatrices(mapX, mapY);

		HighGui.namedWindow(WINDOW_NAME, HighGui.WINDOW_AUTOSIZE);
		//HighGui.resizeWindow(WINDOW_NAME, WIDTH, HEIGHT);

		long time;
		boolean displayEdges = false;
		boolean displayLines = true;
		boolean mirror = true;

		while(HighGui.n_closed_windows == 0){

			time = System.currentTimeMillis();

			vc.read(frame);

			frame = Utils.process(frame, (s, d) -> Imgproc.resize(s, d, size));
			if(mirror) frame = Utils.process(frame, (s, d) -> Imgproc.remap(s, d, mapX, mapY, Imgproc.INTER_LINEAR));

			// Edge detection
			edges = Utils.process(frame, (s, d) -> Imgproc.Canny(s, d, 50, 200, 3, false));

			if(displayEdges) frame = Utils.process(edges, (s, d) -> Imgproc.cvtColor(s, d, Imgproc.COLOR_GRAY2BGR));

			if(displayLines){

				// Probabilistic Hough Line Transform
				Mat lineMatrix = new Mat(); // will hold the results of the detection
				Imgproc.HoughLinesP(edges, lineMatrix, 1, Math.PI / 180, 100, 50, 100); // runs the actual detection

				List<Line> lines = new ArrayList<>();

				// Draw the lines
				for(int i = 0; i < lineMatrix.rows(); i++){

					double[] l = lineMatrix.get(i, 0);
					// Discard lines that are on the edge of the frame, we don't want to detect the edge
					if(l[0] < BORDER && l[2] < BORDER || l[0] > frame.width() - BORDER && l[2] > frame.width() - BORDER
					|| l[1] < BORDER && l[3] < BORDER || l[1] > frame.height() - BORDER && l[3] > frame.height() - BORDER)
						continue;
					// Create the line, add to the list and draw it
					Line line = new Line(l[0], l[1], l[2], l[3]);
					lines.add(line);
					//Imgproc.line(frame, line.getStart(), line.getEnd(), GREEN, 2, Imgproc.LINE_AA, 0);
				}

				lines.sort(Comparator.comparing(Line::angle));

				prevLines.add(lines);

				if(prevLines.size() > INTERP_FRAMES) prevLines.remove(0);

				List<Line> allPrevLines = new ArrayList<>(Utils.flatten(prevLines));
				Collections.reverse(allPrevLines); // Do the more recent lines first

				List<Line> averagedLines = new ArrayList<>();

				while(!allPrevLines.isEmpty()){

					Line ref = allPrevLines.get(0);

					List<Line> coincident = new ArrayList<>();

					for(Line line : allPrevLines){
						// Determine if each line should be considered coincident with ref, and if so add to the list
						if(ref.distanceTo(line.midpoint()) < DISTANCE_THRESHOLD && Line.acuteAngleBetween(ref, line) < ANGLE_THRESHOLD){
							coincident.add(line);
						}
					}

					allPrevLines.removeAll(coincident);

					List<Point> starts = coincident.stream().map(l -> l.xRectified().getStart()).collect(Collectors.toList());
					List<Point> ends = coincident.stream().map(l -> l.xRectified().getEnd()).collect(Collectors.toList());

					Line averageLine = new Line(starts.stream().mapToDouble(p -> p.x).average().orElse(0),
												starts.stream().mapToDouble(p -> p.y).average().orElse(0),
												ends.stream().mapToDouble(p -> p.x).average().orElse(0),
												ends.stream().mapToDouble(p -> p.y).average().orElse(0));

					// Most extreme ends of lines - since we x-rectified them these are simply the max/min x coords
					Point start = starts.stream().min(Comparator.comparingDouble(p -> p.x)).orElse(null);
					Point end = ends.stream().max(Comparator.comparingDouble(p -> p.x)).orElse(null);

					averageLine = new Line(averageLine.nearestPointTo(start), averageLine.nearestPointTo(end));

					averagedLines.add(averageLine);

					Imgproc.line(frame, averageLine.getStart(), averageLine.getEnd(), CYAN, 2, Imgproc.LINE_AA, 0);

				}

				List<Line> centrelines = new ArrayList<>();

				for(int i=0; i<averagedLines.size(); i++){
					Line lineA = averagedLines.get(i);
					Line lineB = averagedLines.get((i+1) % averagedLines.size());
					if(Line.acuteAngleBetween(lineA, lineB) < ANGLE_THRESHOLD){
						Line centreline = Line.equidistant(lineA, lineB);
						centrelines.add(centreline);
						Imgproc.line(frame, centreline.getStart(), centreline.getEnd(), YELLOW, 2, Imgproc.LINE_AA, 0);
					}
				}

				// Super quick fudge to try and read an angle
				if(centrelines.size() >= 2){
					Line lineA = centrelines.get(0);
					Line lineB = centrelines.get(1);
					Imgproc.putText(frame, String.format("%.2f deg", Math.toDegrees(Line.acuteAngleBetween(lineA, lineB))),
							lineA.getStart(), Core.FONT_HERSHEY_PLAIN, 2, WHITE);
				}

			}

			long frameTime = System.currentTimeMillis() - time;
			float fps = 1000f/frameTime;

			Imgproc.putText(frame, "Press Esc to close", textPt0, Core.FONT_HERSHEY_PLAIN, 2, GREEN);
			Imgproc.putText(frame, String.format("%.2f fps", fps), textPt1, Core.FONT_HERSHEY_PLAIN, 2, GREEN);
			Imgproc.putText(frame, String.format("Edges %s (E to toggle)", displayEdges ? "on" : "off"), textPt2, Core.FONT_HERSHEY_PLAIN, 2, GREEN);
			Imgproc.putText(frame, String.format("Lines %s (L to toggle)", displayLines ? "on" : "off"), textPt3, Core.FONT_HERSHEY_PLAIN, 2, GREEN);
			Imgproc.putText(frame, String.format("Mirror %s (M to toggle)", mirror ? "on" : "off"), textPt4, Core.FONT_HERSHEY_PLAIN, 2, GREEN);

			HighGui.imshow(WINDOW_NAME, frame);

			if(HighGui.n_closed_windows > 0) break;

			//System.out.println(1000/FRAMERATE - (int)frameTime);
			int key = HighGui.waitKey(Math.max(5, 1000/FRAMERATE - (int)frameTime));
			if(key == 27) break; // Esc to close
			if(key == 69) displayEdges = !displayEdges; // E to toggle edges
			if(key == 76) displayLines = !displayLines; // L to toggle line detector
			if(key == 77) mirror = !mirror; // M to toggle mirror

		}

		HighGui.destroyAllWindows();
		System.exit(0);
	}

}

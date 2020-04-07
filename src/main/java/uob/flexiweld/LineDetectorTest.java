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

	/** Lines within this angle of each other are considered parallel */
	public static final double ANGLE_THRESHOLD = Math.toRadians(10);
	/** Parallel lines within this distance of each other are considered coincident */
	public static final double DISTANCE_THRESHOLD = 10;
	/** Pairs of parallel, non-coincident lines within this distance of each other are considered to be tubes */
	public static final double WIDTH_THRESHOLD = 200; // Excludes e.g. the edges of the test card

	public static final int BORDER = 150;

	public static final Scalar WHITE = new Scalar(255, 255, 255); // This is blue-green-red for some reason
	public static final Scalar BLACK = new Scalar(0, 0, 0);
	public static final Scalar RED = new Scalar(0, 0, 255);
	public static final Scalar GREEN = new Scalar(0, 255, 0);
	public static final Scalar BLUE = new Scalar(255, 0, 0);
	public static final Scalar CYAN = new Scalar(255, 255, 0);
	public static final Scalar MAGENTA = new Scalar(255, 0, 255);
	public static final Scalar YELLOW = new Scalar(0, 255, 255);

	public static final double ANGLE_RADIUS = 60;
	private static final Size ELLIPSE_SIZE = new Size(ANGLE_RADIUS - 10, ANGLE_RADIUS - 10);

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

				List<Line> lines = extractLines(edges, BORDER);

				lines.sort(Comparator.comparing(Line::angle));

				prevLines.add(lines);

				if(prevLines.size() > INTERP_FRAMES) prevLines.remove(0);

				List<Line> allPrevLines = new ArrayList<>(Utils.flatten(prevLines));
				Collections.reverse(allPrevLines); // Do the more recent lines first

				List<Line> averagedLines = fuzzyAverageLines(allPrevLines, DISTANCE_THRESHOLD, ANGLE_THRESHOLD);

				for(Line line : averagedLines) Imgproc.line(frame, line.getStart(), line.getEnd(), BLUE, 2, Imgproc.LINE_AA, 0);

				List<Line> centrelines = findCentrelines(averagedLines, WIDTH_THRESHOLD, ANGLE_THRESHOLD);
				centrelines.sort(Comparator.comparing(Line::angle).reversed());

				for(Line line : centrelines) Imgproc.line(frame, line.getStart(), line.getEnd(), CYAN, 2, Imgproc.LINE_AA, 0);

				for(int i = 0; i < centrelines.size(); i++){

					Line lineA = centrelines.get(i);

					for(int j = i; j < centrelines.size(); j++){

						Line lineB = centrelines.get(j);

						Point intersection = Line.intersection(lineA, lineB);

						if(intersection != null){

							// Sorting by angle earlier seems to mean line B always has the smaller angle
							double startAngle = lineB.angle();
							double endAngle = lineA.angle();
							double angle = endAngle - startAngle;

							if(angle > Math.PI/2){
								double prevStartAngle = startAngle;
								startAngle = endAngle - Math.PI;
								endAngle = prevStartAngle;
								angle = endAngle - startAngle;
							}

							double midAngle = (startAngle + endAngle) / 2; // Should be fine since centrelines are rectified

							Imgproc.drawMarker(frame, intersection, YELLOW, Imgproc.MARKER_CROSS, 14, 2);

							Imgproc.ellipse(frame, intersection, ELLIPSE_SIZE, 0, Math.toDegrees(startAngle),
									Math.toDegrees(endAngle), YELLOW, 2);

							Imgproc.putText(frame, String.format("%.2fdeg", Math.toDegrees(angle)),
									new Point(intersection.x + ANGLE_RADIUS * Math.cos(midAngle),
											intersection.y  + ANGLE_RADIUS * Math.sin(midAngle) + 10),
									Core.FONT_HERSHEY_PLAIN, 2, YELLOW, 2);
						}
					}
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

	/**
	 * Performs a probabilistic Hough line transform on the given image source and converts the resulting matrix to a
	 * list of {@link Line} objects.
	 * @param source The image from which lines are to be extracted, usually the output of an edge detection filter
	 * @param border Detected lines whose ends both lie within this distance of the edge of the image will be ignored
	 * @return The resulting list of {@code Line} objects (in the order returned by the Hough line transform)
	 */
	private static List<Line> extractLines(Mat source, int border){

		// Probabilistic Hough Line Transform
		Mat lineMatrix = new Mat(); // will hold the results of the detection
		Imgproc.HoughLinesP(source, lineMatrix, 1, Math.PI / 180, 100, 50, 100); // runs the actual detection

		List<Line> lines = new ArrayList<>();

		// Draw the lines
		for(int i = 0; i < lineMatrix.rows(); i++){

			double[] l = lineMatrix.get(i, 0);
			// Discard lines that are on the edge of the frame, we don't want to detect the edge
			if(l[0] < border && l[2] < border || l[0] > source.width() - border && l[2] > source.width() - border
			|| l[1] < border && l[3] < border || l[1] > source.height() - border && l[3] > source.height() - border)
				continue;
			// Create the line, add to the list and draw it
			Line line = new Line(l[0], l[1], l[2], l[3]);
			lines.add(line);
			//Imgproc.line(frame, line.getStart(), line.getEnd(), GREEN, 2, Imgproc.LINE_AA, 0);
		}

		return lines;
	}

	/**
	 * Performs a 'fuzzy average' of the given list of lines. Lines within the given distance and angle thresholds of
	 * each other are considered coincident and are merged by averaging their start and end points.
	 * @param lines A list of {@link Line} objects to be averaged (will not be modified by this method)
	 * @param distThreshold The maximum distance between two lines for them to be considered coincident (the distance
	 *                      between two lines is taken as the smaller of the perpendicular distances from one line to
	 *                      the midpoint of the other)
	 * @param angleThreshold The maximum (acute) angle between two lines for them to be considered coincident
	 * @return The resulting list of averaged lines
	 */
	private static List<Line> fuzzyAverageLines(List<Line> lines, double distThreshold, double angleThreshold){

		List<Line> averagedLines = new ArrayList<>();

		while(!lines.isEmpty()){

			Line ref = lines.get(0);
			// Choose which axis to rectify/compare in based on the gradient of the reference line
			// Avoids issues with vertical lines
			final boolean compareYs = Math.abs(ref.gradient()) > 1;
			lines = lines.stream().map(compareYs ? Line::yRectified : Line::xRectified).collect(Collectors.toList());

			List<Line> coincident = new ArrayList<>();

			for(Line line : lines){
				// Determine if each line should be considered coincident with ref, and if so add to the list
				if(ref.distanceTo(line.midpoint()) < distThreshold && Line.acuteAngleBetween(ref, line) < angleThreshold){
					coincident.add(line);
				}
			}

			lines.removeAll(coincident);

			// Could probably replace all of this with regression or something similar

			List<Point> starts = coincident.stream().map(Line::getStart).collect(Collectors.toList());
			List<Point> ends = coincident.stream().map(Line::getEnd).collect(Collectors.toList());

			Line averageLine = new Line(starts.stream().mapToDouble(p -> p.x).average().orElse(0),
					starts.stream().mapToDouble(p -> p.y).average().orElse(0),
					ends.stream().mapToDouble(p -> p.x).average().orElse(0),
					ends.stream().mapToDouble(p -> p.y).average().orElse(0));

			// Most extreme ends of lines - take the sum of x and y coords to avoid issues with vertical lines
			Point start = starts.stream().min(Comparator.comparingDouble(p -> compareYs ? p.y : p.x)).orElse(null);
			Point end = ends.stream().max(Comparator.comparingDouble(p -> compareYs ? p.y : p.x)).orElse(null);

			averageLine = new Line(averageLine.nearestPointTo(start), averageLine.nearestPointTo(end));

			averagedLines.add(averageLine);
		}

		return averagedLines;
	}

	/**
	 * Detects pairs of (approximately) parallel lines that are less than the specified width apart and returns a list
	 * of lines equidistant from both lines of each pair.
	 * @param lines A list of {@link Line} objects to find centrelines for (will not be modified by this method)
	 * @param widthThreshold The maximum distance between pairs of lines for a centreline to be detected
	 * @param angleThreshold The maximum angle between pairs of lines for a centreline to be detected
	 * @return The resulting list of centrelines
	 */
	private static List<Line> findCentrelines(List<Line> lines, double widthThreshold, double angleThreshold){

		List<Line> centrelines = new ArrayList<>();

		for(int i=0; i<lines.size(); i++){

			Line lineA = lines.get(i);
			Line lineB = lines.get((i+1) % lines.size());

			if(lineA.distanceTo(lineB.midpoint()) < widthThreshold && Line.acuteAngleBetween(lineA, lineB) < angleThreshold){
				Line centreline = Line.equidistant(lineA, lineB).extended(0.25f);
				centrelines.add(centreline);
			}
		}

		return centrelines;
	}

}

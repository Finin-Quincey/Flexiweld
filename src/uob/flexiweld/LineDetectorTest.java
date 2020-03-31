package uob.flexiweld;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.awt.*;

public class LineDetectorTest {

	public static final int CAMERA_NUMBER = 0;
	public static final String WINDOW_NAME = "Line Detection Test";

	public static final int FRAMERATE = 30; // Target framerate in frames per second

	public static final Scalar ANNOTATION_COLOUR = new Scalar(0, 255, 0); // This is blue-green-red for some reason

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
			edges = Utils.process(frame, (s, d) -> Imgproc.Canny(s, d, 200, 250, 3, false));

			if(displayEdges) frame = Utils.process(edges, (s, d) -> Imgproc.cvtColor(s, d, Imgproc.COLOR_GRAY2BGR));

			if(displayLines){
				// Probabilistic Hough Line Transform
				Mat lines = new Mat(); // will hold the results of the detection
				Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, 180, 75, 50); // runs the actual detection
				// Draw the lines
				for(int x = 0; x < lines.rows(); x++){
					double[] l = lines.get(x, 0);
					Imgproc.line(frame, new Point(l[0], l[1]), new Point(l[2], l[3]), ANNOTATION_COLOUR, 2, Imgproc.LINE_AA, 0);
				}
			}

			long frameTime = System.currentTimeMillis() - time;
			float fps = 1000f/frameTime;

			Imgproc.putText(frame, "Press Esc to close", textPt0, Core.FONT_HERSHEY_PLAIN, 2, ANNOTATION_COLOUR);
			Imgproc.putText(frame, String.format("%.2f fps", fps), textPt1, Core.FONT_HERSHEY_PLAIN, 2, ANNOTATION_COLOUR);
			Imgproc.putText(frame, String.format("Edges %s (E to toggle)", displayEdges ? "on" : "off"), textPt2, Core.FONT_HERSHEY_PLAIN, 2, ANNOTATION_COLOUR);
			Imgproc.putText(frame, String.format("Lines %s (L to toggle)", displayLines ? "on" : "off"), textPt3, Core.FONT_HERSHEY_PLAIN, 2, ANNOTATION_COLOUR);
			Imgproc.putText(frame, String.format("Mirror %s (M to toggle)", mirror ? "on" : "off"), textPt4, Core.FONT_HERSHEY_PLAIN, 2, ANNOTATION_COLOUR);

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

package uob.flexiweld;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Calibrator {

	public static final int CAMERA_NUMBER = 0;

	public static final String WINDOW_NAME = "Calibration";

	private static final Size CHESSBOARD_SIZE = new Size(9, 6);
	private static final int CALIBRATION_IMAGES = 10;

	private static final float SQUARE_SIZE_MM = 25.5f;

	public static void main(String[] args){

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		VideoCapture vc = new VideoCapture();
		vc.open(CAMERA_NUMBER);
		System.out.println(vc.isOpened() ? "Video capture opened successfully" : "Failed to open video capture");

		List<Mat> objectPoints = Collections.nCopies(CALIBRATION_IMAGES, generateChessboardPoints(CHESSBOARD_SIZE, SQUARE_SIZE_MM));
		List<Mat> imagePoints = new ArrayList<>();

		Mat frame = new Mat();
		Mat greyscaleFrame = new Mat();

		vc.read(frame);
		Size size = new Size(frame.width(), frame.height());

		// Output matrices
		Mat cameraMatrix = new Mat(3, 3, CvType.CV_32F);
		Mat distCoeffs = new Mat(1, 4, CvType.CV_32F);
		List<Mat> rvecs = new ArrayList<>();
		List<Mat> tvecs = new ArrayList<>();

		while(imagePoints.size() < CALIBRATION_IMAGES){

			int key = -1;

			while(key == -1){ // Press any key to capture calibration image
				vc.read(frame);
				HighGui.imshow(WINDOW_NAME, frame);
				key = HighGui.waitKey(5);
				if(HighGui.n_closed_windows > 0){
					HighGui.destroyAllWindows();
					System.exit(0);
				}
			}

			if(key == 27){ // Esc to abort
				HighGui.destroyAllWindows();
				System.exit(0);
			}

			MatOfPoint2f corners = new MatOfPoint2f();

			// findChessboardCorners works best with a greyscale image
			Imgproc.cvtColor(frame, greyscaleFrame, Imgproc.COLOR_BGR2GRAY);

			boolean foundChessboard = Calib3d.findChessboardCorners(greyscaleFrame, CHESSBOARD_SIZE, corners,
					// Not sure what the flags do, just using the recommended ones for now
					Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE + Calib3d.CALIB_CB_FAST_CHECK);

			if(foundChessboard){
				imagePoints.add(corners);
				System.out.println(String.format("Found chessboard (%s)", imagePoints.size()));
				Calib3d.drawChessboardCorners(frame, CHESSBOARD_SIZE, corners, true);
				HighGui.imshow(WINDOW_NAME, frame);
				HighGui.waitKey(5);
				try{
					Thread.sleep(2000);
				}catch(InterruptedException e){
					e.printStackTrace();
				}
			}else{
				System.out.println("Unable to find chessboard");
			}

		}

		Calib3d.calibrateCamera(objectPoints, imagePoints, size, cameraMatrix, distCoeffs, rvecs, tvecs);

		while(HighGui.n_closed_windows == 0){

			vc.read(frame);

			frame = Utils.process(frame, (s, d) -> Imgproc.undistort(s, d, cameraMatrix, distCoeffs));

			HighGui.imshow(WINDOW_NAME, frame);
			HighGui.waitKey(5);
		}

		HighGui.destroyAllWindows();
		System.exit(0);

	}

	private static Mat generateChessboardPoints(Size size, float squareSize){

		Mat points = new MatOfPoint3f();

		for(int y = 0; y < size.height; ++y){
			for(int x = 0; x < size.width; ++x){
				// A bit unintuitive but hey, we got there in the end!
				points.push_back(new MatOfPoint3f(new Point3(y * squareSize, x * squareSize, 0)));
			}
		}

		return points;
	}

}

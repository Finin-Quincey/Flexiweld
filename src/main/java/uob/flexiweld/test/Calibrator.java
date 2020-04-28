package uob.flexiweld.test;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.opencv.videoio.VideoCapture;
import uob.flexiweld.geom.Line;
import uob.flexiweld.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class containing various calibration functions. This class can be run on its own to test the calibration sequence
 * separately.
 * @author Finin Quincey
 */
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

		// Output matrices
		Mat cameraMatrix = new Mat(3, 3, CvType.CV_32F);
		MatOfDouble distCoeffs = new MatOfDouble(new Mat(1, 4, CvType.CV_64F));
		List<Mat> rvecs = new ArrayList<>();
		List<Mat> tvecs = new ArrayList<>();

		Mat frame = new Mat();

		vc.read(frame);

		Line[] grid = Utils.generateGrid(9, 12, new Size(frame.width(), frame.height()));

		runCalibrationSequence(vc, cameraMatrix, distCoeffs, rvecs, tvecs);

//		// Convert rvecs to full rotation matrices
//		List<Mat> rmats = rvecs.stream().map(m -> Utils.process(m, Calib3d::Rodrigues)).collect(Collectors.toList());
//
//		Mat transform = new Mat(3, 4, rmats.get(0).type());
//		List<Mat> toConcat = new ArrayList<>();
//		toConcat.add(rmats.get(9));
//		toConcat.add(tvecs.get(9));
//		Core.hconcat(toConcat, transform);
//
//		List<Point> projectedPoints = new ArrayList<>();
//
//		for(int y = 0; y < CHESSBOARD_SIZE.height; ++y){
//			for(int x = 0; x < CHESSBOARD_SIZE.width; ++x){
//
//				Mat worldPt = Mat.zeros(4, 1, transform.type()); // MUST BE THE SAME TYPE!
//				worldPt.put(0, 0, x * SQUARE_SIZE_MM);
//				worldPt.put(1, 0, y * SQUARE_SIZE_MM);
//				worldPt.put(1, 0, 0);
//				worldPt.put(3, 0, 1);
//
//				// Find origin vector in image coordinates (i.e. [u; v; 1])
//				Mat originProjection = new Mat();
//				Core.gemm(transform, worldPt, 1, new Mat(), 0, originProjection); // Really intuitive, obvious method name here...
//				projectedPoints.add(new Point(originProjection.get(0, 0))); // Point doesn't care that this has 3 elements
//			}
//		}

		// Re-project corners of last checkerboard to verify
		MatOfPoint2f imagePts = new MatOfPoint2f();
		MatOfPoint3f worldPts = new MatOfPoint3f();
		worldPts.push_back(new MatOfPoint3f(new Point3(0, 0, 0)));
		worldPts.push_back(new MatOfPoint3f(new Point3(0, (CHESSBOARD_SIZE.width - 1) * SQUARE_SIZE_MM, 0)));
		worldPts.push_back(new MatOfPoint3f(new Point3((CHESSBOARD_SIZE.height - 1) * SQUARE_SIZE_MM, 0, 0)));
		worldPts.push_back(new MatOfPoint3f(new Point3((CHESSBOARD_SIZE.height - 1) * SQUARE_SIZE_MM, (CHESSBOARD_SIZE.width - 1) * SQUARE_SIZE_MM, 0)));
		Calib3d.projectPoints(worldPts, rvecs.get(9), tvecs.get(9), cameraMatrix, distCoeffs, imagePts);

		while(HighGui.n_closed_windows == 0){

			vc.read(frame);

			frame = Utils.process(frame, (s, d) -> Imgproc.undistort(s, d, cameraMatrix, distCoeffs));

			//for(Line line : grid) Imgproc.line(frame, line.getStart(), line.getEnd(), Utils.GREEN);

			for(Point point : imagePts.toList()) Imgproc.drawMarker(frame, point, Utils.YELLOW);

			HighGui.imshow(WINDOW_NAME, frame);
			if(HighGui.n_closed_windows > 0) break;
			HighGui.waitKey(5);
		}

		HighGui.destroyAllWindows();
		System.exit(0);

	}

	public static Mat calculateTransformMatrix(Mat rvec, Mat tvec){

		// We shouldn't be applying a camera distortion because a 4-point perspective transform can't possibly account
		// for non-linear effects, and the returned transform is intended for use on undistorted images anyway
		// Therefore (according to the javadoc for projectPoints) we're using an identity cameraMatrix and zero distortion
		Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
		MatOfDouble distCoeffs = new MatOfDouble(Mat.zeros(1, 5, CvType.CV_64F));

		MatOfPoint2f imagePts = new MatOfPoint2f();
		MatOfPoint3f worldPts = new MatOfPoint3f();
		worldPts.push_back(new MatOfPoint3f(new Point3(0, 0, 0)));
		worldPts.push_back(new MatOfPoint3f(new Point3(0, (CHESSBOARD_SIZE.width - 1) * SQUARE_SIZE_MM, 0)));
		worldPts.push_back(new MatOfPoint3f(new Point3((CHESSBOARD_SIZE.height - 1) * SQUARE_SIZE_MM, 0, 0)));
		worldPts.push_back(new MatOfPoint3f(new Point3((CHESSBOARD_SIZE.height - 1) * SQUARE_SIZE_MM, (CHESSBOARD_SIZE.width - 1) * SQUARE_SIZE_MM, 0)));
		// This line effectively *simulates* what the camera is doing and turns the four world points into image points
		Calib3d.projectPoints(worldPts, rvec, tvec, cameraMatrix, distCoeffs, imagePts);

		Mat src = Converters.vector_Point2f_to_Mat(imagePts.toList());
		Mat dst = Converters.vector_Point2f_to_Mat(worldPts.toList().stream().map(p -> new Point(p.x, p.y)).collect(Collectors.toList()));

		return Imgproc.getPerspectiveTransform(src, dst);

	}

	public static void runCalibrationSequence(VideoCapture vc, Mat cameraMatrix, Mat distCoeffs, List<Mat> rvecs, List<Mat> tvecs){

		List<Mat> objectPoints = Collections.nCopies(CALIBRATION_IMAGES, Utils.generateChessboardPoints(CHESSBOARD_SIZE, SQUARE_SIZE_MM));
		List<Mat> imagePoints = new ArrayList<>();

		Mat frame = new Mat();
		Mat greyscaleFrame = new Mat();

		vc.read(frame);
		Size size = new Size(frame.width(), frame.height());

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

			vc.read(frame);

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
	}

}

package uob.flexiweld.app;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import uob.flexiweld.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalibrationMode extends CheckerboardDetectionMode {

	private static final int CALIBRATION_IMAGES = 10; // TODO: Maybe allow this to be changed, or do away with a fixed number entirely

	private final List<Mat> objectPoints;
	private final List<Mat> imagePoints;

	private final CaptureMode originalMode;

	JLabel imageCountReadout;
	JButton captureButton;
	JButton finishButton;
	JButton abortButton;

	public CalibrationMode(Size checkerboardSize, double squareSize, CaptureMode originalMode){
		super("Calibration", checkerboardSize, squareSize);
		this.originalMode = originalMode;
		objectPoints = Collections.nCopies(CALIBRATION_IMAGES, Utils.generateChessboardPoints(checkerboardSize, squareSize));
		imagePoints = new ArrayList<>();
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		super.populateControls(app, components);
		components.add(captureButton = FlexiweldApp.createButton("\u2795 Capture", e -> captureCalibrationPoints(app)));
		components.add(finishButton = FlexiweldApp.createButton(FlexiweldApp.CHECK_MARK + "Finish", e -> finishCalibration(app)));
		components.add(abortButton = FlexiweldApp.createButton(FlexiweldApp.CROSS_SYMBOL + "Abort", e -> abort(app)));
		finishButton.setForeground(FlexiweldApp.CONFIRM_TEXT_COLOUR);
		finishButton.setEnabled(false);
		abortButton.setForeground(FlexiweldApp.ERROR_TEXT_COLOUR);
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		imageCountReadout = FlexiweldApp.addStatusText(getImageCountStatus(0), components);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){
		frame = super.processFrame(videoFeed, frame);
		return frame;
	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){
		captureButton.setEnabled(!finishButton.isEnabled() && foundCheckerboard());
		return super.annotateFrame(videoFeed, frame);
	}

	private String getImageCountStatus(int captured){
		return String.format("Capturing calibration images (%s/%s)", captured, CALIBRATION_IMAGES);
	}

	private void captureCalibrationPoints(FlexiweldApp app){

		if(!foundCheckerboard()) return; // Should never happen but just in case

		app.getVideoFeed().pauseFor(1000);

		imagePoints.add(getCorners());
		imageCountReadout.setText(getImageCountStatus(imagePoints.size()));
		if(imagePoints.size() == objectPoints.size()){
			finishButton.setEnabled(true);
			captureButton.setEnabled(false);
		}
	}

	private void finishCalibration(FlexiweldApp app){

		if(imagePoints.size() != objectPoints.size()) return; // Should never happen but just in case

		// Output matrices
		Mat cameraMatrix = new Mat(3, 3, CvType.CV_32F);
		MatOfDouble distCoeffs = new MatOfDouble(new Mat(1, 4, CvType.CV_64F));
		List<Mat> rvecs = new ArrayList<>();
		List<Mat> tvecs = new ArrayList<>();

		Calib3d.calibrateCamera(objectPoints, imagePoints, app.getVideoFeed().getCameraResolution(), cameraMatrix, distCoeffs, rvecs, tvecs);

		// Calibration always discards the alignment matrix (because it's no longer valid!)
		app.setMode(new MeasurementMode(cameraMatrix, distCoeffs, null));
	}

	/**
	 * Exits calibration mode and reverts to the previous settings, after first prompting the user to confirm they wish
	 * to discard any calibration progress (if no images were captured, the prompt is skipped).
	 */
	private void abort(FlexiweldApp app){

		if(imagePoints.size() > 0){ // If no images were captured, don't warn the user

			int choice = JOptionPane.showOptionDialog(app.getFrame(), "Abort calibration and revert to previous settings?",
					"Confirm exit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
					new String[]{"Abort", "Continue"}, "Continue");

			if(choice != JOptionPane.OK_OPTION) return;
		}

		app.setMode(originalMode); // Revert to the original mode
	}

}

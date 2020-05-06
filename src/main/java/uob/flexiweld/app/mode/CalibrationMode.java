package uob.flexiweld.app.mode;

import com.sun.istack.internal.Nullable;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import uob.flexiweld.util.Utils;
import uob.flexiweld.util.CalibrationSettings;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CalibrationMode extends CheckerboardDetectionMode {

	private final FileNameExtensionFilter clbFilter = new FileNameExtensionFilter("Calibration files (*.clb)", "clb");

	private static final int CALIBRATION_IMAGES = 10; // TODO: Maybe allow this to be changed, or do away with a fixed number entirely

	private final List<Mat> objectPoints;
	private final List<Mat> imagePoints;

	@Nullable
	private CalibrationSettings calibrationSettings; // N.B. This isn't final here
	@Nullable
	private Mat alignmentMatrix; // N.B. This isn't final here

	JLabel progressReadout;
	JButton captureButton;
	JButton finishButton;
	JButton exitButton;
	JButton saveButton;
	JButton loadButton;

	public CalibrationMode(Size checkerboardSize, double squareSize, @Nullable CalibrationSettings calibrationSettings, @Nullable Mat alignmentMatrix){
		super("Calibration", checkerboardSize, squareSize);
		this.calibrationSettings = calibrationSettings;
		this.alignmentMatrix = alignmentMatrix;
		objectPoints = Collections.nCopies(CALIBRATION_IMAGES, Utils.generateChessboardPoints(checkerboardSize, squareSize));
		imagePoints = new ArrayList<>();
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		super.populateControls(app, components);
		components.add(captureButton = FlexiweldApp.createButton("\u2795 Capture", e -> captureCalibrationPoints(app)));
		components.add(finishButton = FlexiweldApp.createButton(FlexiweldApp.CHECK_MARK + "Finish", e -> finishCalibration(app)));
		components.add(saveButton = FlexiweldApp.createButton("\ud83d\udcbe Save Settings", e -> saveToFile(app)));
		components.add(loadButton = FlexiweldApp.createButton("\ud83d\udcc2 Load Settings", e -> loadFromFile(app)));
		components.add(exitButton = FlexiweldApp.createButton("\u21a9 Exit", e -> exit(app)));
		finishButton.setForeground(FlexiweldApp.CONFIRM_TEXT_COLOUR);
		finishButton.setEnabled(false);
		saveButton.setEnabled(calibrationSettings != null);
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		progressReadout = FlexiweldApp.addStatusText(getImageCountStatus(0), components);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){
		frame = super.processFrame(videoFeed, frame);
		return frame;
	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){
		captureButton.setEnabled(!finishButton.isEnabled() && foundCheckerboard());
		saveButton.setEnabled(calibrationSettings != null);
		return super.annotateFrame(videoFeed, frame);
	}

	private String getImageCountStatus(int captured){
		return String.format("Capturing calibration images (%s/%s)", captured, CALIBRATION_IMAGES);
	}

	private void captureCalibrationPoints(FlexiweldApp app){

		if(!foundCheckerboard()) return; // Should never happen but just in case

		app.getVideoFeed().pauseFor(1000);

		imagePoints.add(getCorners());
		progressReadout.setText(getImageCountStatus(imagePoints.size()));
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

		calibrationSettings = new CalibrationSettings(cameraMatrix, distCoeffs);
		alignmentMatrix = null; // Calibration always discards the alignment matrix (because it's no longer valid!)
		imagePoints.clear(); // Wipe the images now, we're done with them
		finishButton.setEnabled(false);
		progressReadout.setText("Calibration successful");

	}

	/**
	 * Exits calibration mode and reverts to the previous settings, after first prompting the user to confirm they wish
	 * to discard any calibration progress (if no images were captured, the prompt is skipped).
	 */
	private void exit(FlexiweldApp app){

		if(imagePoints.size() > 0){ // If no images were captured or calibration is done, don't warn the user

			int choice = JOptionPane.showOptionDialog(app.getFrame(), "Exit calibration and revert to previous settings?",
					"Confirm exit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
					new String[]{"Exit", "Cancel"}, "Cancel");

			if(choice != JOptionPane.OK_OPTION) return;
		}

		app.setMode(new MeasurementMode(calibrationSettings, alignmentMatrix)); // Revert to the previous settings
	}

	private void saveToFile(FlexiweldApp app){

		String path = app.saveWithOverwriteWarning(clbFilter);

		if(path != null){

			if(!calibrationSettings.save(path)){
				JOptionPane.showMessageDialog(app.getFrame(), "Unable to save calibration settings",
						"Error", JOptionPane.ERROR_MESSAGE);
				progressReadout.setText("Unable to save calibration settings");
			}else{
				progressReadout.setText("Calibration settings saved to " + Utils.ellipsise(path, 50, true));
			}
		}
	}

	private void loadFromFile(FlexiweldApp app){

		app.fileChooser.setFileFilter(clbFilter);

		int result = app.fileChooser.showOpenDialog(app.getFrame());

		if(result == JFileChooser.APPROVE_OPTION){

			File file = app.fileChooser.getSelectedFile();
			String path = file.getPath();

			if(Arrays.stream(clbFilter.getExtensions()).noneMatch(s -> file.getName().endsWith(s))){
				path = path + ".clb";
			}

			CalibrationSettings newSettings = CalibrationSettings.load(path);

			if(newSettings != null){

				if(imagePoints.size() > 0){ // If no images were captured or calibration is done, don't warn the user
					int choice = JOptionPane.showOptionDialog(app.getFrame(), "Discard current calibration progress?",
							"Confirm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
							new String[]{"Ok", "Cancel"}, "Cancel");
					if(choice != JOptionPane.OK_OPTION) return;
				}

				calibrationSettings = newSettings;
				imagePoints.clear(); // Wipe the images now, we don't need them any more
				finishButton.setEnabled(false);
				captureButton.setEnabled(false);
				progressReadout.setText("Calibration settings loaded from " + Utils.ellipsise(path, 50, true));

			}else{
				JOptionPane.showMessageDialog(app.getFrame(), "Unable to load calibration settings",
						"Error", JOptionPane.ERROR_MESSAGE);
				progressReadout.setText("Unable to load calibration settings");
			}
		}
	}

}

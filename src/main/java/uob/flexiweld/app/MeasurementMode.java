package uob.flexiweld.app;

import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import uob.flexiweld.Utils;

import java.awt.*;
import java.util.List;

public class MeasurementMode extends LiveMode {

	private final Mat cameraMatrix;
	private final MatOfDouble distCoeffs;
	private final Mat alignmentMatrix;

	public MeasurementMode(){
		this(null, null, null);
	}

	public MeasurementMode(Mat cameraMatrix, MatOfDouble distCoeffs, Mat alignmentMatrix){
		super("Measuring");
		this.cameraMatrix = cameraMatrix;
		this.distCoeffs = distCoeffs;
		this.alignmentMatrix = alignmentMatrix;
	}

	public boolean isCalibrated(){
		return cameraMatrix != null && distCoeffs != null;
	}

	public boolean isAligned(){
		return alignmentMatrix != null;
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		super.populateControls(app, components);
		components.add(FlexiweldApp.createButton("\u25a9 " + (isCalibrated() ? "Recalibrate" : "Calibrate"), e -> prepareCalibration(app)));
		components.add(FlexiweldApp.createButton("\u25a3 " + (isAligned() ? "Realign" : "Align"), e -> prepareAlignment(app)));
	}

	private void prepareCalibration(FlexiweldApp app){
		// TODO: Dialogue box that prompts the user for the calibration parameters below
		app.setMode(new CalibrationMode(new Size(9, 6), 25.5, this));
	}

	private void prepareAlignment(FlexiweldApp app){
		// TODO: Dialogue box that prompts the user for the alignment parameters below
		app.setMode(new AlignmentMode(new Size(9, 6), 25.5, cameraMatrix, distCoeffs, alignmentMatrix));
	}

	@Override
	public void populateStatusBar(List<Component> components){

		super.populateStatusBar(components);

		if(isCalibrated()){
			FlexiweldApp.addConfirmText("Calibrated", components);
		}else{
			FlexiweldApp.addWarningText("Not calibrated", components);
		}

		if(isAligned()){
			FlexiweldApp.addConfirmText("Aligned", components);
		}else{
			FlexiweldApp.addWarningText("Not aligned", components);
		}
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){

		frame = super.processFrame(videoFeed, frame);

		if(isCalibrated()){
			frame = Utils.process(frame, (s, d) -> Imgproc.undistort(s, d, cameraMatrix, distCoeffs)); // Lens correction
		}

		return frame;
	}

}

package uob.flexiweld.app;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class CheckerboardDetectionMode extends LiveMode {

	/** The number of intersections in each direction the checkerboard has (one less than the number of squares). */
	protected final Size checkerboardSize;
	/** The size of each square of the checkerboard in millimetres. */
	protected final double squareSize;

	private Mat greyscaleFrame;
	private MatOfPoint2f corners;
	private boolean foundCheckerboard;

	JLabel patternDetectedReadout;

	public CheckerboardDetectionMode(String name, Size checkerboardSize, double squareSize){
		super(name);
		this.checkerboardSize = checkerboardSize;
		this.squareSize = squareSize;
		corners = new MatOfPoint2f();
		greyscaleFrame = new Mat();
	}

	public boolean foundCheckerboard(){
		return foundCheckerboard;
	}

	public MatOfPoint2f getCorners(){
		return corners;
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		patternDetectedReadout = FlexiweldApp.addErrorText("No checkerboard detected", components);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat raw){

		// findChessboardCorners works best with a greyscale image
		Imgproc.cvtColor(raw, greyscaleFrame, Imgproc.COLOR_BGR2GRAY);

		foundCheckerboard = Calib3d.findChessboardCorners(greyscaleFrame, checkerboardSize, corners,
				// Not sure what the flags do, just using the recommended ones for now
				Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE + Calib3d.CALIB_CB_FAST_CHECK);

		if(foundCheckerboard){
			Calib3d.drawChessboardCorners(raw, checkerboardSize, corners, true);
			patternDetectedReadout.setText(FlexiweldApp.CHECK_MARK + "Found checkerboard");
			patternDetectedReadout.setForeground(FlexiweldApp.CONFIRM_TEXT_COLOUR);
		}else{
			patternDetectedReadout.setText(FlexiweldApp.CROSS_SYMBOL + "No checkerboard detected");
			patternDetectedReadout.setForeground(FlexiweldApp.ERROR_TEXT_COLOUR);
		}

		return super.processFrame(videoFeed, raw);

	}

}

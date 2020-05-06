package uob.flexiweld.app.mode;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;

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

	/**
	 * Returns a copy of the matrix of checkerboard corners detected in the most recent frame (see
	 * {@link Calib3d#findChessboardCorners(Mat, Size, MatOfPoint2f, int)}). Since the returned matrix is a copy, it
	 * is local to this method and may be modified externally.
	 */
	public MatOfPoint2f getCorners(){
		return new MatOfPoint2f(corners.clone()); // This MUST be cloned or it will get overwritten next frame!
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		patternDetectedReadout = FlexiweldApp.addErrorText("No checkerboard detected", components);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){

		// findChessboardCorners works best with a greyscale image
		Imgproc.cvtColor(frame, greyscaleFrame, Imgproc.COLOR_BGR2GRAY);

		// No need to wipe or (worse) re-create the corners matrix every frame since it is more efficient just to let
		// findChessboardCorners overwrite it every frame - however, this means it MUST be fully encapsulated, see the
		// comment in getCorners() above
		foundCheckerboard = Calib3d.findChessboardCorners(greyscaleFrame, checkerboardSize, corners,
				// Not sure what the flags do, just using the recommended ones for now
				Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE + Calib3d.CALIB_CB_FAST_CHECK);

		return frame;

	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){

		if(foundCheckerboard){
			Calib3d.drawChessboardCorners(frame, checkerboardSize, videoFeed.transformForDisplay(corners), true);
			patternDetectedReadout.setText(FlexiweldApp.CHECK_MARK + "Found checkerboard");
			patternDetectedReadout.setForeground(FlexiweldApp.CONFIRM_TEXT_COLOUR);
		}else{
			patternDetectedReadout.setText(FlexiweldApp.CROSS_SYMBOL + "No checkerboard detected");
			patternDetectedReadout.setForeground(FlexiweldApp.ERROR_TEXT_COLOUR);
		}

		return super.annotateFrame(videoFeed, frame);
	}
}

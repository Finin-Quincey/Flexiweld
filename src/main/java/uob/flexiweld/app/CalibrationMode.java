package uob.flexiweld.app;

import org.opencv.core.Mat;
import org.opencv.core.Size;

public class CalibrationMode extends CheckerboardDetectionMode {

	public CalibrationMode(Size checkerboardSize, double squareSize){
		super("Calibration", checkerboardSize, squareSize);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat raw){
		return super.processFrame(videoFeed, raw);
	}

}

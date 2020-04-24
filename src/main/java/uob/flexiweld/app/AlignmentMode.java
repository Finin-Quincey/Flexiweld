package uob.flexiweld.app;

import org.opencv.core.Mat;
import org.opencv.core.Size;

public class AlignmentMode extends CheckerboardDetectionMode {

	public AlignmentMode(Size checkerboardSize, double squareSize){
		super("Alignment", checkerboardSize, squareSize);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat raw){
		return super.processFrame(videoFeed, raw);
	}

}

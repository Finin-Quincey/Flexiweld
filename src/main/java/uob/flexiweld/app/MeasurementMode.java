package uob.flexiweld.app;

import org.opencv.core.Mat;

public class MeasurementMode extends CaptureMode {

	public MeasurementMode(){
		super("Measurement");
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat raw){
		return raw;
	}

}

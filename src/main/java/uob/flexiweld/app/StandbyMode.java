package uob.flexiweld.app;

import org.opencv.core.Mat;

import java.awt.*;
import java.util.List;

public class StandbyMode extends CaptureMode {

	public StandbyMode(){
		super("Standby");
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		components.add(FlexiweldApp.createButton("\u25b6 Start", e -> app.toggleCamera()));
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){
		return frame; // Do nothing (will never be called anyway)
	}

}

package uob.flexiweld.app;

import org.opencv.core.Mat;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class LiveMode extends CaptureMode {

	private JLabel fpsReadout;

	public LiveMode(String name){
		super(name);
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		components.add(FlexiweldApp.createButton("\u23f9 Stop", e -> app.toggleCamera()));
		JToggleButton pauseButton = FlexiweldApp.createToggleButton("\u275a\u275a Pause", e -> app.getVideoFeed().togglePause());
		pauseButton.setSelected(app.getVideoFeed().isPaused());
		components.add(pauseButton);
		components.add(FlexiweldApp.createButton("\ud83d\udcf7 Snapshot", e -> {}));
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		fpsReadout = FlexiweldApp.addStatusText("", components);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat raw){
		fpsReadout.setText(String.format("%.4g fps", videoFeed.getFps()));
		return raw; // Do nothing
	}

}

package uob.flexiweld.app;

import org.opencv.core.Core;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Implements a simple user interface to control the prototype tube detection software, for demonstration and testing
 * purposes.
 * @author Finin Quincey
 */
public class FlexiweldApp {

	public static final int FRAMERATE = 50;

	private final VideoFeed videoFeed;

	private JFrame jFrame;
	private Timer timer;

	private JPanel controlPanel;

	private JPanel globalControls;
	private JPanel contextControls;

	private JPanel statusBar;
	private JLabel fpsReadout;

	private JPanel videoPanel;
	private JLabel videoContainer;

	public FlexiweldApp(){

//		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		int width = 1280;//screenSize.width;
		int height = 960;//screenSize.height;

		// Create and set up the window
		jFrame = new JFrame("Flexiweld Demo App");
		jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e){
				onResize();
			}
		});

		videoFeed = new VideoFeed(0);

		initPane(jFrame.getContentPane());

		// Use the content pane's default BorderLayout. No need for
		// setLayout(new BorderLayout());
		// Display the window
		jFrame.pack();
		jFrame.setSize(width, height);
		jFrame.setVisible(true);

		timer = new Timer(1000/FRAMERATE, e -> this.update());
		timer.setInitialDelay(50);
		timer.start();

	}

	private void initPane(Container pane){

		if(!(pane.getLayout() instanceof BorderLayout)){
			pane.add(new JLabel("Container doesn't use BorderLayout!"));
			return;
		}

		statusBar = new JPanel();
		statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
		statusBar.setBackground(Color.DARK_GRAY);
		statusBar.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY.brighter()));

		statusBar.add(fpsReadout = new JLabel(""));
		fpsReadout.setForeground(Color.WHITE);
		statusBar.add(Box.createHorizontalStrut(100));
		statusBar.add(new JLabel("Some other status"));

		pane.add(statusBar, BorderLayout.SOUTH);

		controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
		controlPanel.setBackground(Color.DARK_GRAY);
		controlPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY.brighter()));
		controlPanel.add(new JLabel("Menu"));

		globalControls = new JPanel();
		globalControls.setLayout(new GridLayout(12, 1, 10, 10));
		globalControls.setBackground(Color.LIGHT_GRAY);

//		for(String font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()){
//			System.out.println(font);
//		}

		JToggleButton button = new JToggleButton("Start/Stop");
		button.setFont(new Font("Segoe UI", Font.PLAIN, 20));
		button.addActionListener(e -> this.toggleCamera(button));
		globalControls.add(button);

		controlPanel.add(globalControls);

		pane.add(controlPanel, BorderLayout.WEST);

		videoPanel = new JPanel();
		videoPanel.setLayout(new BorderLayout());
		videoPanel.setBackground(Color.DARK_GRAY);

		videoContainer = new JLabel((ImageIcon)null); // Will be initialised later
		videoContainer.setVerticalTextPosition(SwingConstants.CENTER);
		videoContainer.setFont(new Font("Segoe UI", Font.PLAIN, 20));
		videoContainer.setForeground(Color.WHITE);

		videoPanel.add(videoContainer, BorderLayout.CENTER);

		pane.add(videoPanel, BorderLayout.CENTER);

	}

	private void toggleCamera(JToggleButton button){
		if(button.getModel().isSelected()){
			videoContainer.setText("Opening camera...");
			if(videoFeed.start()){
				videoFeed.fit(videoPanel.getSize().width, videoPanel.getSize().height);
			}else{
				button.getModel().setSelected(false); // Unpress the button
				JOptionPane.showMessageDialog(jFrame, "Cannot open camera, try closing other applications",
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}else{
			videoFeed.stop();
		}
	}

	private void onResize(){
		if(videoFeed.isRunning()){
			videoFeed.fit(videoPanel.getSize().width, videoPanel.getSize().height);
		}
	}

	private void update(){

		if(videoFeed.isRunning()){
			videoContainer.setIcon(new ImageIcon(videoFeed.update()));
			videoContainer.setText(null);
			fpsReadout.setText(String.format("%.2f fps", videoFeed.getFps()));
		}else{
			videoContainer.setIcon(null);
			videoContainer.setText("Press Start/Stop to start the live video feed");
		}

		jFrame.repaint();

	}

	public static void main(String[] args){
		// Load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
//		try{
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//		}catch(ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException e){
//			e.printStackTrace();
//		}
		// Schedule a job for the event dispatch thread:
		// creating and showing this application's GUI.
		SwingUtilities.invokeLater(FlexiweldApp::new);
	}

}
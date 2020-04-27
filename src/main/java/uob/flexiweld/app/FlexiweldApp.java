package uob.flexiweld.app;

import org.opencv.core.Core;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a simple user interface to control the prototype tube detection software, for demonstration and testing
 * purposes.
 * @author Finin Quincey
 */
public class FlexiweldApp {

	public static final int FRAMERATE = 50;

	public static final Font BUTTON_FONT = new Font("Segoe UI Symbol", Font.PLAIN, 24);
	public static final Font STATUS_BAR_FONT = new Font("Segoe UI Symbol", Font.PLAIN, 16);

	public static final Color CONTROLS_BG_COLOUR = new Color(0x646464);
	public static final Color BUTTON_BG_COLOUR = new Color(0x404040);
	public static final Color BUTTON_FG_COLOUR = new Color(0xcaa4ff);

	public static final Color STATUS_TEXT_COLOUR = Color.WHITE;
	public static final Color CONFIRM_TEXT_COLOUR = Color.GREEN;
	public static final Color WARNING_TEXT_COLOUR = Color.ORANGE;
	public static final Color ERROR_TEXT_COLOUR = new Color(0xFF4747);

	public static final String CHECK_MARK = "\u2714 ";
	public static final String WARNING_SYMBOL = "\u26a0 ";
	public static final String CROSS_SYMBOL = "\u274c ";

	public static final int STATUS_TEXT_SPACING = 30;

	/** The {@link VideoFeed} object that controls the connection to the camera and the main processing sequence. */
	private final VideoFeed videoFeed;

	/** The capturing mode the application is currently in. */
	private CaptureMode mode;

	// GUI Components
	private JFrame jFrame;
	private Timer timer;

	private JPanel controlPanel;
	private JPanel controls;

	private JPanel statusBar;

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

		mode = new StandbyMode();// new CalibrationMode(new Size(9, 6), 25);

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

		initStatusBar();

		pane.add(statusBar, BorderLayout.SOUTH);

		controlPanel = new JPanel();
		controlPanel.setLayout(new BorderLayout());
		controlPanel.setBackground(Color.DARK_GRAY);
		controlPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY.brighter()));

		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream inputStream = classloader.getResourceAsStream("assets/images/logo.png");

		try{
			if(inputStream != null){
				BufferedImage logo = ImageIO.read(inputStream);
				Image logoScaled = logo.getScaledInstance(180, logo.getHeight() * 180/logo.getWidth(), Image.SCALE_SMOOTH);
				JLabel logoLabel = new JLabel(new ImageIcon(logoScaled));
				controlPanel.add(logoLabel, BorderLayout.NORTH);
			}
		}catch(IOException e){
			e.printStackTrace();
		}

		controls = new JPanel();
		controls.setLayout(new GridLayout(12, 1, 5, 5));
		controls.setBackground(CONTROLS_BG_COLOUR);
		controls.setPreferredSize(new Dimension(180, 0)); // Height is irrelevant
		controls.setBorder(BorderFactory.createLineBorder(CONTROLS_BG_COLOUR, 5));

//		for(String font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()){
//			System.out.println(font);
//		}

		initControlPanel();

		controlPanel.add(controls, BorderLayout.CENTER);

		pane.add(controlPanel, BorderLayout.WEST);

		videoPanel = new JPanel();
		videoPanel.setLayout(new BorderLayout());
		videoPanel.setBackground(Color.DARK_GRAY);

		videoContainer = new JLabel((ImageIcon)null); // Will be initialised later
		videoContainer.setVerticalTextPosition(SwingConstants.CENTER);
		videoContainer.setFont(BUTTON_FONT);
		videoContainer.setForeground(Color.WHITE);

		videoPanel.add(videoContainer, BorderLayout.CENTER);

		pane.add(videoPanel, BorderLayout.CENTER);

	}

	private void initStatusBar(){

		statusBar.removeAll();

		// Use a list so modes can't modify the status bar itself
		List<Component> components = new ArrayList<>();
		mode.populateStatusBar(components);
		components.forEach(statusBar::add);
	}

	private void initControlPanel(){

		controls.removeAll();

		// Use a list so modes can't modify the control panel itself
		List<Component> components = new ArrayList<>();
		mode.populateControls(this, components);
		components.forEach(controls::add);
	}

	/** Returns the app's {@link VideoFeed} instance. */
	public VideoFeed getVideoFeed(){
		return videoFeed;
	}

	/** Sets the current capture mode for the application and updates the interface accordingly. */
	public void setMode(CaptureMode mode){
		this.mode = mode;
		initStatusBar();
		initControlPanel();
	}

	public void toggleCamera(){
		if(videoFeed.isRunning()){
			videoFeed.stop();
			setMode(new StandbyMode());
		}else{
			videoContainer.setText("Opening camera...");
			if(videoFeed.start()){
				videoFeed.fit(videoPanel.getSize().width, videoPanel.getSize().height);
				setMode(new MeasurementMode());
			}else{
				initStatusBar();
				List<Component> components = new ArrayList<>();
				addErrorText("Unable to open camera", components);
				components.forEach(statusBar::add);
				JOptionPane.showMessageDialog(jFrame, "Cannot open camera, try closing other applications",
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void onResize(){
		if(videoFeed.isRunning()){
			videoFeed.fit(videoPanel.getSize().width, videoPanel.getSize().height);
		}
	}

	private void update(){

		if(videoFeed.isRunning()){
			videoContainer.setIcon(new ImageIcon(videoFeed.update(mode)));
			videoContainer.setText(null);
		}else{
			videoContainer.setIcon(null);
			videoContainer.setText("Press Start to start the live video feed");
		}

		jFrame.repaint();

	}

	/**
	 * Helper method to create a new {@link JButton} with the standard style for the control panel. Neatens up the code.
	 * @param text The text to display on the button
	 * @param listener The callback to execute when the button is pressed
	 * @return the resulting {@code JButton}
	 */
	public static JButton createButton(String text, ActionListener listener){
		JButton button = new JButton(text);
		button.setFont(BUTTON_FONT);
		button.setBackground(BUTTON_BG_COLOUR);
		button.setForeground(BUTTON_FG_COLOUR);
		button.addActionListener(listener);
		return button;
	}

	/**
	 * Helper method to create a new {@link JToggleButton} with the standard style for the control panel. Neatens up
	 * the code.
	 * @param text The text to display on the button
	 * @param listener The callback to execute when the button is pressed
	 * @return the resulting {@code JToggleButton}
	 */
	public static JToggleButton createToggleButton(String text, ActionListener listener){
		JToggleButton button = new JToggleButton(text);
		button.setFont(BUTTON_FONT);
		button.setBackground(BUTTON_BG_COLOUR);
		button.setForeground(BUTTON_FG_COLOUR);
		button.addActionListener(listener);
		return button;
	}

	/**
	 * Helper method to add a new {@link JLabel} with the standard style for the status bar, followed by the
	 * standard-width space between status bar elements. Neatens up the code.
	 * @param text The text to display
	 * @return the resulting {@code JLabel}
	 */
	public static JLabel addStatusText(String text, List<Component> components){
		JLabel label = new JLabel(text);
		label.setFont(STATUS_BAR_FONT);
		label.setForeground(STATUS_TEXT_COLOUR);
		components.add(label);
		components.add(Box.createHorizontalStrut(STATUS_TEXT_SPACING));
		return label;
	}

	public static JLabel addConfirmText(String text, List<Component> components){
		JLabel label = addStatusText(CHECK_MARK + text, components);
		label.setForeground(CONFIRM_TEXT_COLOUR);
		return label;
	}

	public static JLabel addWarningText(String text, List<Component> components){
		JLabel label = addStatusText(WARNING_SYMBOL + text, components);
		label.setForeground(WARNING_TEXT_COLOUR);
		return label;
	}

	public static JLabel addErrorText(String text, List<Component> components){
		JLabel label = addStatusText(CROSS_SYMBOL + text, components);
		label.setForeground(ERROR_TEXT_COLOUR);
		return label;
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
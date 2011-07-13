package mihai.calibration;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import mihai.tracker.ObjectTrackerPanel;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;

public class Ui extends JFrame implements ActionListener, Broadcaster.Listener
{
    private static final long serialVersionUID = 1L;
    
    private Config config = null;
    private String configPath = null;
    private ArrayList<String> urls;
    private boolean settingsDone = false;
    
    private int currentCard = 0;
    private final static int INTRO = 0;
    private final static int CONFIG = 1;
    private final static int CAMERAS = 2;
    private final static int EXTRINSICS = 3;
    private final static int TRACK = 4;
    private final static int INTRINSICS = 5; // this is last so intrinsics+# can be unique
    
    private JPanel mainPanel;
    
    private JButton nextButton;
    private JButton backButton;
    private String next = "Next";
    private String back = "Back";

    public Ui()
    {
        super("Multi-Camera Calibrator");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(Toolkit.getDefaultToolkit().getImage(System.getenv("APRIL_DOCS") + File.separator+"april-logo.png"));
        
        urls = new ArrayList<String>();
        mainPanel = new JPanel();
        mainPanel.setLayout(new CardLayout());
        
        try
        {
            mainPanel.add(new IntroPanel(INTRO), Integer.toString(INTRO));
            mainPanel.add(new ConfigPanel(CONFIG), Integer.toString(CONFIG));
            
            for(Component b : mainPanel.getComponents())
            {
                ((Broadcaster)b).setListener(this);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
        JPanel welcomePanel = new JPanel(new BorderLayout());
        JLabel welcome = new JLabel("Multi-Camera Calibrator", JLabel.CENTER);
        welcome.setFont(new Font("Serif", Font.PLAIN, 32));
        welcomePanel.add(welcome, BorderLayout.NORTH);
        welcomePanel.add(new JLabel("presented by the APRIL Lab", JLabel.CENTER), BorderLayout.CENTER);
        welcomePanel.add(new JSeparator(), BorderLayout.SOUTH);
        
        nextButton = new JButton(next);
        nextButton.addActionListener(this);
        backButton = new JButton(back);
        backButton.addActionListener(this);
        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
        buttonBox.add(backButton);
        buttonBox.add(Box.createHorizontalStrut(10));
        buttonBox.add(nextButton);
        buttonBox.add(Box.createHorizontalStrut(30));
        JSeparator separator = new JSeparator();
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(separator, BorderLayout.NORTH);
        buttonPanel.add(buttonBox, java.awt.BorderLayout.EAST);

        add(welcomePanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        setCurrentCard(INTRO);
        
        setVisible(true);
    }
    
    public static void main(String[] args)
    {
        new Ui();
    }

    public void setCurrentCard(int card)
    {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        nextButton.setEnabled(true);
        backButton.setEnabled(true);
        
        ((Broadcaster)mainPanel.getComponent(currentCard)).stop();
        
        try
        {
            if(card == INTRO)
            {
                backButton.setEnabled(false);
                setSize(screenSize.width/2, screenSize.height);
            }
            else if(card == CONFIG)
            {
                nextButton.setEnabled(false);
                setSize(screenSize.width/2, screenSize.height);
            }
            else if(card >= INTRINSICS)
            {
                ((Broadcaster)mainPanel.getComponent(card)).go(configPath, urls.get(card-INTRINSICS));
                setSize(screenSize.width, screenSize.height);
            }
            else if(card == CAMERAS)
            {
                ((Broadcaster)mainPanel.getComponent(card)).go(configPath, urls.toArray(new String[urls.size()]));
                ((Broadcaster)mainPanel.getComponent(card)).displayMsg("", false);
                setSize(screenSize.width, screenSize.height);
            }
            else if(card == EXTRINSICS)
            {
                nextButton.setEnabled(false);
                backButton.setEnabled(false);
                ((Broadcaster)mainPanel.getComponent(card)).go(configPath, urls.toArray(new String[urls.size()]));
                setSize(screenSize.width, screenSize.height);
            }
            else if(card == TRACK)
            {
                nextButton.setEnabled(false);
                ((Broadcaster)mainPanel.getComponent(card)).go(configPath, urls.toArray(new String[urls.size()]));
                setSize(screenSize.width, screenSize.height);
            }
            else
            {
                throw new InvalidCardException();   
            }
        } catch (InvalidCardException e)
        {
            e.printStackTrace();
        }

        currentCard = card;
        ((CardLayout)mainPanel.getLayout()).show(mainPanel, Integer.toString(card));
        System.out.println("flipping to " + currentCard);
    }
    
    public void gotoNextCard() throws InvalidCardException
    {
        if(currentCard == INTRO)
        {
            setCurrentCard(CONFIG);
        }
        else if(currentCard == CONFIG)
        {
            System.out.println(INTRINSICS);
            setCurrentCard(INTRINSICS);
        }
        else if(currentCard >= INTRINSICS)
        {
            if(currentCard+1 == INTRINSICS+urls.size())
            {
                setCurrentCard(CAMERAS);
            }
            else
            {
                setCurrentCard(INTRINSICS+1);
            }
        }
        else if(currentCard == CAMERAS)
        {
            setCurrentCard(EXTRINSICS);
        }
        else if(currentCard == EXTRINSICS)
        {
            setCurrentCard(TRACK);
        }
    }
    
    public void gotoLastCard() throws InvalidCardException
    {
        if(currentCard == CONFIG)
        {
            setCurrentCard(INTRO);
        }
        else if(currentCard == INTRINSICS)
        {
            setCurrentCard(CONFIG);
        }
        else if(currentCard > INTRINSICS)
        {
            setCurrentCard(INTRINSICS-1);
        }
        else if(currentCard == CAMERAS)
        {
            setCurrentCard(INTRINSICS+urls.size()-1);
        }
        else if(currentCard == EXTRINSICS)
        {
            setCurrentCard(CAMERAS);
        }
        else if(currentCard == TRACK)
        {
            setCurrentCard(EXTRINSICS);
        }
    }
    
    public void actionPerformed(ActionEvent event)
    {
        if(event.getActionCommand().equals(next))
        {
            try
            {
                gotoNextCard();
            } catch (InvalidCardException e)
            {
                e.printStackTrace();
            }
        }
        else if(event.getActionCommand().equals(back))
        {
            try
            {
                gotoLastCard();
            } catch (InvalidCardException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void handle(int id, boolean ready, String ...info )
    {
        if(id == currentCard)
        {
            if(id == EXTRINSICS)
            {
                if(ready == true)
                {
                    nextButton.setEnabled(true);
                    backButton.setEnabled(true);
                }
                else
                {
                    setCurrentCard(CAMERAS);
                    ((Broadcaster)mainPanel.getComponent(CAMERAS)).displayMsg("Error: no tags detected", true);
                }
            }
            else if(id == CONFIG)
            {
                try
                {
                    configPath = info[0];
                    config = new ConfigFile(configPath);
                    Util.verifyConfig(config);
                    nextButton.setEnabled(ready);
                } catch (IOException e)
                {
                    JOptionPane.showMessageDialog(this, "IO Exception: Unable to open file");
                    e.printStackTrace();
                    
                } catch (ConfigException e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                    e.printStackTrace();
                }
                
                settingsDone = ready;
                if(settingsDone)
                {
                    try
                    {
                        ArrayList<String> allUrls = ImageSource.getCameraURLs();
                        
                        for(String url : allUrls)
                        {
                            if(Util.isValidUrl(config, url))
                            {
                                urls.add(url);
                            }
                        }

                        mainPanel.add(new CameraPlayerPanel(CAMERAS, 2), Integer.toString(CAMERAS));
                        mainPanel.add(new ExtrinsicsPanel(EXTRINSICS, urls.toArray(new String[urls.size()])), Integer.toString(EXTRINSICS));
                        mainPanel.add(new ObjectTrackerPanel(TRACK, true), Integer.toString(TRACK));

                        int x = 0;
                        for(String url: urls)
                        {
                            mainPanel.add(new IntrinsicsPanel(INTRINSICS+x, url), Integer.toString(INTRINSICS+x));
                            x++;
                        }
                        
                        for(Component b : mainPanel.getComponents())
                        {
                            ((Broadcaster)b).setListener(this);
                        }
                    } catch (ConfigException e)
                    {
                        e.printStackTrace();
                    } catch (CameraException e)
                    {
                        e.printStackTrace();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

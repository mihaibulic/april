package mihai.calibration;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import javax.swing.JCheckBox;
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

public class UI extends JFrame implements ActionListener, Broadcaster.Listener
{
    private static final long serialVersionUID = 1L;
    
    private Config config = null;
    private String configPath = null;
    private ArrayList<String> urls = new ArrayList<String>();
    
    private int currentCard = 0;
    private final static String CONFIG = "Co";
    private final static String EXTRINSICS = "Ex";
    private ArrayList<Broadcaster> cards = new ArrayList<Broadcaster>();
    private JPanel mainPanel = new JPanel(new CardLayout());
    
    private String next = "Next";
    private String back = "Back";
    private String directions = "Show directions";
    private JButton nextButton = new JButton(next);
    private JButton backButton = new JButton(back);
    private JCheckBox directionsBox = new JCheckBox(directions, true);

    public UI()
    {
        super("Multi-Camera Calibrator");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(d.width/2, d.height);
        setIconImage(Toolkit.getDefaultToolkit().getImage(System.getenv("APRIL_DOCS") + File.separator+"april-logo.png"));
        
        try
        {
            add(new IntroPanel());
            add(new ConfigPanel(CONFIG));
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
        
        nextButton.addActionListener(this);
        backButton.addActionListener(this);
        directionsBox.addActionListener(this);
//        directionsBox.setVisible(false);
        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
        buttonBox.add(directionsBox);
        buttonBox.add(Box.createHorizontalStrut(10));
        buttonBox.add(backButton);
        buttonBox.add(Box.createHorizontalStrut(10));
        buttonBox.add(nextButton);
        buttonBox.add(Box.createHorizontalStrut(30));
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(new JSeparator(), BorderLayout.NORTH);
        buttonPanel.add(buttonBox, java.awt.BorderLayout.EAST);

        add(welcomePanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        try
        {
            setCard(0);
        } catch (InvalidCardException e)
        {
            e.printStackTrace();
        }
        
        setVisible(true);
    }
    
    public static void main(String[] args)
    {
        new UI();
    }

    public void setCard(int card) throws InvalidCardException
    {
        if(card >= 0 && card < cards.size())
        {
            cards.get(currentCard).stop();
            
            CardLayout cl = (CardLayout)mainPanel.getLayout();
            cl.show(mainPanel, Integer.toString(card));
            currentCard = card;
            cards.get(currentCard).go(configPath, urls.toArray(new String[urls.size()]));
            cards.get(currentCard).showDirections(directionsBox.isSelected());
        }
        else
        {
            throw new InvalidCardException();
        }

    }
    
    public void next() throws InvalidCardException
    {
        setCard(currentCard+1);
    }
    
    public void back() throws InvalidCardException
    {
        setCard(currentCard-1);
    }
    
    public void actionPerformed(ActionEvent event)
    {
        if(event.getActionCommand().equals(next))
        {
            try
            {
                next();
            } catch (InvalidCardException e)
            {
                e.printStackTrace();
            }
        }
        else if(event.getActionCommand().equals(back))
        {
            try
            {
                back();
            } catch (InvalidCardException e)
            {
                e.printStackTrace();
            }
        }
        else if(event.getActionCommand().equals(directions))
        {
            cards.get(currentCard).showDirections(directionsBox.isSelected());
        }
    }
    
    private void add(Broadcaster newCard)
    {
        newCard.setListener(this);
        mainPanel.add(newCard, cards.size()+"");
        cards.add(newCard);
    }

    public void handle(String id, boolean ready, String ...info )
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
                try
                {
                    back();
                } catch (InvalidCardException e)
                {
                    e.printStackTrace();
                }
                cards.get(currentCard).displayMsg("Error: no tags detected", true);
            }
        }
        else if(id.equals(CONFIG))
        {
            
            if(ready)
            {
                try
                {
                    configPath = info[0];
                    config = new ConfigFile(configPath);
                    Util.verifyConfig(config);
                    
                    ArrayList<String> allUrls = ImageSource.getCameraURLs();
                    urls.clear();
                    
                    for(String url : allUrls)
                    {
                        if(Util.isValidUrl(config, url))
                        {
                            add(new IntrinsicsPanel(cards.size()+"", urls.size())); 
                            urls.add(url);
                        }
                    }

                    if(urls.isEmpty())
                    {
                        JOptionPane.showMessageDialog(this, "No cameras found.  Please plug them in an reclick the config file.");
                    }
                    else
                    {
                        nextButton.setEnabled(true);
                        add(new CameraPlayerPanel(2, true));
                        add(new ExtrinsicsPanel(EXTRINSICS));
                        add(new ObjectTrackerPanel(true));
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
            else
            {
                nextButton.setEnabled(false);
            }
        }
    }
}

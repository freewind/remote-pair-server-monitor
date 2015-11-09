package com.thoughtworks.pli.remotepair.monitor;

import javax.swing.*;
import java.awt.event.*;

public class _MainDialog extends JDialog {
    private JPanel contentPane;
    protected JTextField _serverAddressTextField;
    protected JButton _connectButton;
    protected JTree _fileTree;
    protected JList<models.DocEventItemData> _docEventList;
    protected JLabel _filePathLabel;
    protected JLabel _serverVersionLabel;
    protected JButton _closeButton;
    protected JTextPane _fileContentTextPane;

    public _MainDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(_connectButton);

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
// add your code here
        dispose();
    }

    private void onCancel() {
// add your code here if necessary
        dispose();
    }

}

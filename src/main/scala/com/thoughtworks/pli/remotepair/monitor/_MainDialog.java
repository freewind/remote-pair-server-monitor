package com.thoughtworks.pli.remotepair.monitor;

import javax.swing.*;
import java.awt.event.*;

public class _MainDialog extends JDialog {
    private JPanel contentPane;
    protected JTextField serverAddressTextField;
    protected JButton connectButton;
    protected JTree fileTree;
    protected JTextArea fileContentTextArea;
    protected JList<models.VersionNodeData> fileVersionList;
    protected JLabel filePathLabel;
    protected JLabel serverVersionLabel;

    public _MainDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(connectButton);

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

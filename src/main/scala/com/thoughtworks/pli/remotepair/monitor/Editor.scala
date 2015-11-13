package com.thoughtworks.pli.remotepair.monitor

import java.awt.Color
import java.awt.event.{MouseEvent, FocusEvent}
import javax.swing.text.DefaultCaret

import scala.swing.TextArea

class Editor extends TextArea {editor =>
  enabled = true
  editable = true
  peer.setCaret(new ReadOnlyCaret)
  caret.color = Color.red

  def setText(text: String, caret: Option[Int]) = {
    editor.text = text
    caret match {
      case Some(offset) =>
        editor.caret.visible = true
        editor.caret.position = offset
      case None =>
        editor.caret.visible = false
    }
  }

  def clear(): Unit = setText("", None)
}

class ReadOnlyCaret extends DefaultCaret {
  override def focusLost(e: FocusEvent): Unit = {
    super.focusLost(e)
    setVisible(true)
  }
  override def mouseClicked(e: MouseEvent): Unit = ()
  override def mousePressed(e: MouseEvent): Unit = ()
  override def mouseDragged(e: MouseEvent): Unit = ()
  override def moveCaret(e: MouseEvent): Unit = ()
}

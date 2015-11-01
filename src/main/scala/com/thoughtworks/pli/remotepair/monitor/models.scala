package com.thoughtworks.pli.remotepair.monitor

object models {

  case class VersionItemData(version: Int, editorName: String) {
    override def toString: String = editorName + ": " + version.toString
  }

}

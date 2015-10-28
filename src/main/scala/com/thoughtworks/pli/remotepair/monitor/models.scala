package com.thoughtworks.pli.remotepair.monitor

object models {

  case class VersionNodeData(version: Int) {
    override def toString: String = version.toString
  }

}

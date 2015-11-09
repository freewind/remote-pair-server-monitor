package com.thoughtworks.pli.remotepair.monitor

object HtmlCodeContentGenerator {

  def convert(text: String): String = {
//    val subTexts = split(text, carets)
//    val html = subTexts.zip(repeat(carets.length, "<span class='caret'>|</span>") ++ Seq("")).map({ case (a, b) => a + b }).mkString("")
    s"<pre>$text</pre>"
  }

  private def repeat(times: Int, str: String): Seq[String] = (0 until times).map(_ => str)
  private def split(text: String, offsets: Seq[Int]): Seq[String] = offsets match {
    case x :: rest => text.substring(0, x) +: split(text.substring(x), rest.map(_ - x))
    case Nil => List(text)
  }

}

package com.thoughtworks.pli.remotepair.monitor

import org.specs2.mutable.Specification

class HtmlCodeContentGeneratorSpec extends Specification {

  "HtmlCodeContentGenerator should" >> {
    "wrap text in <pre></pre>" >> {
      HtmlCodeContentGenerator.convert("abc") === "<pre>abc</pre>"
    }
    //    "wrap caret in <span class='caret'>|</span>" >> {
    //      "for a caret" >> {
    //        HtmlCodeContentGenerator.convert("abcdef", carets = Seq(2)) === "<pre>ab<span class='caret'>|</span>cdef</pre>"
    //      }
    //      "for multi carets" >> {
    //        HtmlCodeContentGenerator.convert("abcdef", carets = Seq(2, 4)) === "<pre>ab<span class='caret'>|</span>cd<span class='caret'>|</span>ef</pre>"
    //      }
    //    }
    //    "wrap insert in <span class='add'></span>" >> {
    //      "for a single insert" >> {
    //        HtmlCodeContentGenerator.convert("abcdef", Seq(Insert(3, "123"))) === "<pre>abc<span class='add'>123</span>def</pre>"
    //      }
    //      "for multi inserts" >> {
    //        HtmlCodeContentGenerator.convert("abcdef", Seq(Insert(2, "1"), Insert(4, "23"))) === "<pre>ab<span class='add'>1</span>c<span class='add'>23</span>def</pre>"
    //      }
    //    }
    //    "wrap delete in <span class='delete'></span>" >> {
    //      "for a single delete" >> {
    //        todo
    //      }
    //      "for multi deletes" >> {
    //        todo
    //      }
    //    }
    //    "handle inserts, deletes, carets" >> {
    //      todo
    //    }
  }

}

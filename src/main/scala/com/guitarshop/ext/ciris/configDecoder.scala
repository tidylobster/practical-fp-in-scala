package com.guitarshop.ext.ciris

import ciris.ConfigDecoder
import com.guitarshop.ext.derevo.Derive

object configDecoder extends Derive[Decoder.Id]

object Decoder {
  type Id[A] = ConfigDecoder[String, A]
}

package com.guitarshop.ext

import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.Size

object refined {

  implicit def validateSizeN[N <: Int, R](implicit w: ValueOf[N]): Validate.Plain[R, Size[N]] =
    Validate.fromPredicate[R, Size[N]](
      _.toString.size == w.value,
      _ => s"Must be ${w.value} digits",
      Size[N](w.value)
    )

}

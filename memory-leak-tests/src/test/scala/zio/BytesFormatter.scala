package zio

import java.text.{DecimalFormat, NumberFormat}
import java.util.Locale

object BytesFormatter {
  private val formatter = NumberFormat.getInstance(Locale.US).asInstanceOf[DecimalFormat]
  private val symbols   = formatter.getDecimalFormatSymbols

  symbols.setGroupingSeparator('_')
  formatter.setDecimalFormatSymbols(symbols)

  implicit class BytesFmt(private val x: Long) extends AnyVal {
    def fmtBytes: String = formatter.format(x)
    def fmtDelta: String =
      if (x > 0) s"+${x.fmtBytes}" else x.fmtBytes
  }
}

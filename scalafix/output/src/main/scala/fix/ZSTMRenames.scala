package fix

import zio.stm.ZSTM

object ZSTMRenames {
  ZSTM.collectAllDiscard _
  ZSTM.foreachDiscard _
  ZSTM.access _
  ZSTM.accessSTM _
  ZSTM.ifSTM _
  ZSTM.loopDiscard _
  ZSTM.attempt _
  ZSTM.replicateSTM _
  ZSTM.replicateSTMDiscard _
  ZSTM.unlessSTM _
  ZSTM.whenCaseSTM _
  ZSTM.whenSTM _
}

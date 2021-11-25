/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.stream

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZPipelineCompanionVersionSpecific {

  implicit final class ZPipelineSyntax[LowerEnv, UpperEnv, LowerErr, UpperErr, LowerElem, UpperElem, OutEnv[
    Env
  ], OutErr[Err], OutElem[Elem]](
    private val self: ZPipeline.WithOut[
      LowerEnv,
      UpperEnv,
      LowerErr,
      UpperErr,
      LowerElem,
      UpperElem,
      OutEnv,
      OutErr,
      OutElem
    ]
  ) {

    /**
     * Composes two pipelines into one pipeline, by first applying the
     * transformation of the specified pipeline, and then applying the
     * transformation of this pipeline.
     */
    def <<<[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, LowerElem2, UpperElem2, OutEnv2[Env], OutErr2[Err], OutElem2[
      Elem
    ]](
      that: ZPipeline.WithOut[
        LowerEnv2,
        UpperEnv2,
        LowerErr2,
        UpperErr2,
        LowerElem2,
        UpperElem2,
        OutEnv2,
        OutErr2,
        OutElem2
      ]
    )(implicit
      composeEnv: ZCompose[LowerEnv2, UpperEnv2, OutEnv2, LowerEnv, UpperEnv, OutEnv],
      composeErr: ZCompose[LowerErr2, UpperErr2, OutErr2, LowerErr, UpperErr, OutErr],
      composeElem: ZCompose[LowerElem2, UpperElem2, OutElem2, LowerElem, UpperElem, OutElem]
    ): ZPipeline.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeElem.Lower,
      composeElem.Upper,
      composeEnv.Out,
      composeErr.Out,
      composeElem.Out
    ] =
      that >>> self

    /**
     * Composes two pipelines into one pipeline, by first applying the
     * transformation of this pipeline, and then applying the transformation of
     * the specified pipeline.
     */
    def >>>[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, LowerElem2, UpperElem2, OutEnv2[Env], OutErr2[Err], OutElem2[
      Elem
    ]](
      that: ZPipeline.WithOut[
        LowerEnv2,
        UpperEnv2,
        LowerErr2,
        UpperErr2,
        LowerElem2,
        UpperElem2,
        OutEnv2,
        OutErr2,
        OutElem2
      ]
    )(implicit
      composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, OutEnv2],
      composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, OutErr2],
      composeElem: ZCompose[LowerElem, UpperElem, OutElem, LowerElem2, UpperElem2, OutElem2]
    ): ZPipeline.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeElem.Lower,
      composeElem.Upper,
      composeEnv.Out,
      composeErr.Out,
      composeElem.Out
    ] =
      new ZPipeline[
        composeEnv.Lower,
        composeEnv.Upper,
        composeErr.Lower,
        composeErr.Upper,
        composeElem.Lower,
        composeElem.Upper
      ] {
        type OutEnv[Env]   = composeEnv.Out[Env]
        type OutErr[Err]   = composeErr.Out[Err]
        type OutElem[Elem] = composeElem.Out[Elem]
        def apply[
          Env >: composeEnv.Lower <: composeEnv.Upper,
          Err >: composeErr.Lower <: composeErr.Upper,
          Elem >: composeElem.Lower <: composeElem.Upper
        ](
          stream: ZStream[Env, Err, Elem]
        )(implicit trace: ZTraceElement): ZStream[OutEnv[Env], OutErr[Err], OutElem[Elem]] = {
          val left  = self.asInstanceOf[ZPipeline[Nothing, Any, Nothing, Any, Nothing, Any]](stream)
          val right = that.asInstanceOf[ZPipeline[Nothing, Any, Nothing, Any, Nothing, Any]](left)
          right.asInstanceOf[ZStream[OutEnv[Env], OutErr[Err], OutElem[Elem]]]
        }
      }

    /**
     * Composes two pipelines into one pipeline, by first applying the
     * transformation of this pipeline, and then applying the transformation of
     * the specified pipeline.
     */
    def @@[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, LowerElem2, UpperElem2, OutEnv2[Env], OutErr2[Err], OutElem2[
      Elem
    ]](
      that: ZPipeline.WithOut[
        LowerEnv2,
        UpperEnv2,
        LowerErr2,
        UpperErr2,
        LowerElem2,
        UpperElem2,
        OutEnv2,
        OutErr2,
        OutElem2
      ]
    )(implicit
      composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, OutEnv2],
      composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, OutErr2],
      composeElem: ZCompose[LowerElem, UpperElem, OutElem, LowerElem2, UpperElem2, OutElem2]
    ): ZPipeline.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeElem.Lower,
      composeElem.Upper,
      composeEnv.Out,
      composeErr.Out,
      composeElem.Out
    ] =
      self >>> that

    /**
     * A named version of the `>>>` operator.
     */
    def andThen[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, LowerElem2, UpperElem2, OutEnv2[Env], OutErr2[
      Err
    ], OutElem2[Elem]](
      that: ZPipeline.WithOut[
        LowerEnv2,
        UpperEnv2,
        LowerErr2,
        UpperErr2,
        LowerElem2,
        UpperElem2,
        OutEnv2,
        OutErr2,
        OutElem2
      ]
    )(implicit
      composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, OutEnv2],
      composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, OutErr2],
      composeElem: ZCompose[LowerElem, UpperElem, OutElem, LowerElem2, UpperElem2, OutElem2]
    ): ZPipeline.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeElem.Lower,
      composeElem.Upper,
      composeEnv.Out,
      composeErr.Out,
      composeElem.Out
    ] =
      self >>> that

    /**
     * A named version of the `<<<` operator.
     */
    def compose[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, LowerElem2, UpperElem2, OutEnv2[Env], OutErr2[
      Err
    ], OutElem2[Elem]](
      that: ZPipeline.WithOut[
        LowerEnv2,
        UpperEnv2,
        LowerErr2,
        UpperErr2,
        LowerElem2,
        UpperElem2,
        OutEnv2,
        OutErr2,
        OutElem2
      ]
    )(implicit
      composeEnv: ZCompose[LowerEnv2, UpperEnv2, OutEnv2, LowerEnv, UpperEnv, OutEnv],
      composeErr: ZCompose[LowerErr2, UpperErr2, OutErr2, LowerErr, UpperErr, OutErr],
      composeElem: ZCompose[LowerElem2, UpperElem2, OutElem2, LowerElem, UpperElem, OutElem]
    ): ZPipeline.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeElem.Lower,
      composeElem.Upper,
      composeEnv.Out,
      composeErr.Out,
      composeElem.Out
    ] =
      self <<< that
  }
}

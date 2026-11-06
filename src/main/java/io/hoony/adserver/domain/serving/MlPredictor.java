package io.hoony.adserver.domain.serving;

import java.util.OptionalDouble;

/**
 * [모듈 04] 랭킹 엔진에서 머신러닝(Triton/gRPC 등) 모델 서버를 호출해
 * 실시간 예측 클릭률(pCTR)을 받아오는 추론 접점.
 */
@FunctionalInterface
public interface MlPredictor {

    OptionalDouble predictCtr(Long adId, String slotId);
}

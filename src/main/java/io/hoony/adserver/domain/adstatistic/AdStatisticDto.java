package io.hoony.adserver.domain.adstatistic;

public record AdStatisticDto(
        long impressions,
        long clicks
) {
    public double getSmoothedCtr(double alpha, double beta) {
        return (double) (clicks + alpha) / (impressions + beta);
    }
}

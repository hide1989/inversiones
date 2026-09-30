package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.ReturnSegment;

/** Wire representation of a {@link ReturnSegment}. */
public record ReturnSegmentDto(String segment, MoneyDto invested, MoneyDto marketValue, MoneyDto assetGain) {

    public static ReturnSegmentDto from(ReturnSegment segment) {
        return new ReturnSegmentDto(
                segment.segment().name(),
                MoneyDto.from(segment.invested()),
                MoneyDto.from(segment.marketValue()),
                MoneyDto.from(segment.assetGain()));
    }
}

package com.yushan.gamification_service.config;

import com.yushan.gamification_service.entity.YuanReservation;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Custom TypeHandler for ReservationStatus enum conversion
 */
@MappedTypes(YuanReservation.ReservationStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ReservationStatusTypeHandler extends BaseTypeHandler<YuanReservation.ReservationStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, YuanReservation.ReservationStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public YuanReservation.ReservationStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? YuanReservation.ReservationStatus.valueOf(value) : null;
    }

    @Override
    public YuanReservation.ReservationStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value != null ? YuanReservation.ReservationStatus.valueOf(value) : null;
    }

    @Override
    public YuanReservation.ReservationStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value != null ? YuanReservation.ReservationStatus.valueOf(value) : null;
    }
}



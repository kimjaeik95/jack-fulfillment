package com.fulfillment.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * 콤마로 이어붙인 문자열을 List&lt;String&gt; 으로 매핑한다.
 *
 * 목록 조회에서 1:N 을 그대로 조인하면 행이 늘어나 페이징이 깨진다.
 * PostgreSQL 의 string_agg 로 한 행에 묶어 가져온 뒤 여기서 나눈다.
 *
 *   (SELECT string_agg(r.role_id, ',' ORDER BY r.sort_order) ...) AS role_ids
 *
 * 값이 없으면 null 이 아니라 빈 리스트를 돌려준다.
 * 화면에서 null 검사를 빠뜨려 터지는 것을 막기 위한 것이다.
 */
@MappedTypes(List.class)
public class CsvListTypeHandler extends BaseTypeHandler<List<String>> {

	private static final String DELIMITER = ",";

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setString(i, String.join(DELIMITER, parameter));
	}

	@Override
	public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return split(rs.getString(columnName));
	}

	@Override
	public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return split(rs.getString(columnIndex));
	}

	@Override
	public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return split(cs.getString(columnIndex));
	}

	private List<String> split(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(DELIMITER))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}

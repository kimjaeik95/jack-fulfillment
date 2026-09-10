package com.fulfillment.system.user.dao;

import com.fulfillment.domain.User;
import com.fulfillment.system.user.dto.UserSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 사용자 조회 · 등록 · 수정. 물리 삭제는 제공하지 않는다. */
public interface UserDao {

	List<User> selectList(UserSearch search);

	long countList(UserSearch search);

	/** 단건 조회. 배정 역할까지 함께 채운다. */
	User selectByUserId(@Param("userId") String userId);

	/** 존재 여부만 확인 (중복 검사용) */
	int countByUserId(@Param("userId") String userId);

	/** 이메일 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByEmail(@Param("email") String email, @Param("exceptUserId") String exceptUserId);

	/** 등록 후 userSeq 가 채워진다 */
	void insert(User user);

	void update(User user);

	/** 물리 삭제 대신 퇴사 처리 (요구사항: 사용자 물리 삭제 금지) */
	void retire(@Param("userSeq") Long userSeq, @Param("updatedBy") String updatedBy);

	void deleteRoles(@Param("userSeq") Long userSeq);

	void insertRoles(@Param("userSeq") Long userSeq,
			@Param("roleIds") List<String> roleIds,
			@Param("createdBy") String createdBy);

	/** 비밀번호 변경 — 변경 강제 플래그를 함께 해제한다 */
	void updatePassword(@Param("userSeq") Long userSeq,
			@Param("passwordHash") String passwordHash,
			@Param("mustChange") String mustChange,
			@Param("updatedBy") String updatedBy);

	/** 잠금 해제 — 실패 횟수도 함께 초기화해야 즉시 다시 잠기지 않는다 */
	void unlock(@Param("userSeq") Long userSeq, @Param("updatedBy") String updatedBy);
}

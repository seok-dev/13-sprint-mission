package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.command.binarycontent.BinaryContentCreateCommand;
import com.sprint.mission.discodeit.dto.command.user.UserCreateCommand;
import com.sprint.mission.discodeit.dto.command.user.UserUpdateCommand;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.event.BinaryContentCreatedEvent;
import com.sprint.mission.discodeit.event.RoleUpdatedEvent;
import com.sprint.mission.discodeit.exception.user.UserAlreadyExistsException;
import com.sprint.mission.discodeit.exception.user.UserNotFoundException;
import com.sprint.mission.discodeit.mapper.UserMapper;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.security.jwt.JwtRegistry;
import com.sprint.mission.discodeit.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BasicUserService implements UserService {

    private final UserRepository userRepository;
    private final BinaryContentRepository binaryContentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReadStatusRepository readStatusRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtRegistry jwtRegistry;

    @Override
    public UserDto createUser(UserCreateCommand command,
                              BinaryContentCreateCommand profileRequest) {

        //userName 중복 검사
        if (userRepository.existsByUsername(command.username())){
            log.warn("사용자 생성 실패 - 중복된 username: {}", command.username());
            throw UserAlreadyExistsException.withName(command.username());
        }

        //email 중복 체크
        if (userRepository.existsByEmail(command.email())) {
            log.warn("사용자 생성 실패 - 중복된 email: {}", command.email());
            throw UserAlreadyExistsException.withEmail(command.email());
        }

        //프로필 이미지 처리
        BinaryContent profile = null;
        if (profileRequest != null) {
            profile = binaryContentRepository.save(new BinaryContent(
                    profileRequest.fileName(), profileRequest.fileSize(), profileRequest.contentType()));
            eventPublisher.publishEvent(
                    new BinaryContentCreatedEvent(
                            profile.getId(),
                            profileRequest.bytes()
                    )
            );
        }
        String encodedPassword = passwordEncoder.encode(command.password());
        User user = new User(command.username(), command.email(), encodedPassword, Role.USER, profile);
        userRepository.save(user);

        log.info("유저 생성 완료 - name: {}, userId: {}", command.username(),  user.getId());
        return  userMapper.toDto(user, false);

    }

    @Transactional(readOnly = true)
    @Override
    public UserDto findByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        log.debug("유저 조회 - name: {}", user.getUsername());

        return userMapper.toDto(user, jwtRegistry.hasActiveJwtInformationByUserId(userId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<UserDto> findAllUser() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            return  new ArrayList<>();
        }
        log.debug("전체 유저 조회 완료 - 총 {}명", users.size());
        return users.stream()
                .map(user -> userMapper.toDto(user, jwtRegistry.hasActiveJwtInformationByUserId(user.getId())))
                .toList();
    }

    @Override
    @PreAuthorize("principal.userDto.id().equals(#userId)")
    public UserDto updateUser(UUID userId, UserUpdateCommand command, BinaryContentCreateCommand profileRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        //프로필 이미지 선택적 처리
        if (profileRequest != null) {
            BinaryContent oldProfile = user.getProfile();

            BinaryContent profile = binaryContentRepository.save(new BinaryContent(
                    profileRequest.fileName(), profileRequest.fileSize(), profileRequest.contentType()));
            eventPublisher.publishEvent(
                    new BinaryContentCreatedEvent(
                            profile.getId(),
                            profileRequest.bytes()
                    )
            );
            user.updateUserProfileId(profile);

            if (oldProfile != null) {
                binaryContentRepository.delete(oldProfile);
            }
        }

        if (command.newUsername() != null) {
            if (userRepository.existsByUsername(command.newUsername())){
                log.warn("이름 변경 실패 - 중복된 username: {}", command.newUsername());
                throw UserAlreadyExistsException.withName(command.newUsername());
            }
            user.updateUserName(command.newUsername());
        }

        if (command.newEmail() != null) {
            if (userRepository.existsByEmail(command.newEmail())) {
                log.warn("이메일 변경 실패 - 중복된 email: {}", command.newEmail());
                throw UserAlreadyExistsException.withEmail(command.newEmail());
            }
            user.updateUserEmail(command.newEmail());
        }

        if (command.newPassword() != null)
            user.updateUserPassword(passwordEncoder.encode(command.newPassword()));
        userRepository.save(user);

        log.info("유저 수정 완료 -  name: {}, userId: {}", user.getUsername(), user.getId());

        return userMapper.toDto(user, jwtRegistry.hasActiveJwtInformationByUserId(userId));
    }

    @Override
    @PreAuthorize("principal.userDto.id().equals(#userId)")
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(()->UserNotFoundException.withId(userId));
        BinaryContent profile = user.getProfile();
        readStatusRepository.deleteByUserId(userId);
        userRepository.delete(user);
        if (profile != null) {
            binaryContentRepository.delete(profile);
        }
        jwtRegistry.invalidateJwtInformationByUserId(userId);
        log.info("유저 삭제 - name: {}, userId: {}", user.getUsername(), user.getId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public UserDto updateRole(UUID userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        Role oldRole = user.getRole();

        user.updateRole(newRole);
        userRepository.save(user);

        jwtRegistry.invalidateJwtInformationByUserId(userId);

        if (oldRole != newRole) {
            eventPublisher.publishEvent(
                    new RoleUpdatedEvent(
                            userId,
                            oldRole,
                            newRole
                    )
            );
        }

        log.info("권한 수정 완료 - userId: {}, newRole: {}", user.getId(), newRole);

        return userMapper.toDto(user, false);
    }
}

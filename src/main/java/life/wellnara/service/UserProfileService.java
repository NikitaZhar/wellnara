package life.wellnara.service;

import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for user personal data (profile) operations.
 *
 * <p>Owns creation, retrieval and update of {@link UserProfile},
 * and resolves the human-facing display name used across provider and client pages.
 */
@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    /**
     * Creates user profile service.
     *
     * @param userProfileRepository repository for user profiles
     */
    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * Creates a profile for the given user.
     *
     * @param user      profile owner
     * @param firstName user first name
     * @param lastName  user last name
     * @param phone     user phone number, optional (may be null or blank)
     * @return saved profile
     */
    @Transactional
    public UserProfile createProfile(User user, String firstName, String lastName, String phone) {
        String validatedFirstName = requireNonBlank(firstName, "First name is required");
        String validatedLastName = requireNonBlank(lastName, "Last name is required");

        UserProfile profile = new UserProfile(
                user,
                validatedFirstName,
                validatedLastName,
                normalizePhone(phone)
        );

        return userProfileRepository.save(profile);
    }

    /**
     * Returns the profile of the given user.
     *
     * @param user profile owner
     * @return found profile
     */
    @Transactional(readOnly = true)
    public UserProfile getProfile(User user) {
        return userProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
    }

    /**
     * Returns the profile of the given user if it exists.
     *
     * <p>Safe for display paths and for users created before profiles existed.
     *
     * @param user profile owner
     * @return profile or empty result
     */
    @Transactional(readOnly = true)
    public java.util.Optional<UserProfile> findProfile(User user) {
        return userProfileRepository.findByUser(user);
    }

    /**
     * Updates the profile of the given user.
     *
     * @param user      profile owner
     * @param firstName new first name
     * @param lastName  new last name
     * @param phone     new phone number, optional (may be null or blank)
     */
    @Transactional
    public void updateProfile(User user, String firstName, String lastName, String phone) {
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseGet(() -> new UserProfile(
                        user,
                        requireNonBlank(firstName, "First name is required"),
                        requireNonBlank(lastName, "Last name is required"),
                        normalizePhone(phone)
                ));

        profile.setFirstName(requireNonBlank(firstName, "First name is required"));
        profile.setLastName(requireNonBlank(lastName, "Last name is required"));
        profile.setPhone(normalizePhone(phone));

        userProfileRepository.save(profile);
    }

    /**
     * Resolves the human-facing display name for the given user.
     *
     * <p>Returns "First Last" when a profile exists, otherwise falls back to the login nickname.
     *
     * @param user user whose display name is requested
     * @return display name, never null
     */
    @Transactional(readOnly = true)
    public String resolveDisplayName(User user) {
        return userProfileRepository.findByUser(user)
                .map(UserProfile::getFullName)
                .orElseGet(user::getUsername);
    }

    /**
     * Loads profiles for a set of users in a single query, keyed by user id.
     *
     * <p>Intended for rendering a page of users (client list, appointment list)
     * without issuing one query per user.
     *
     * @param users users whose profiles are requested
     * @return map of user id to profile; users without a profile are absent
     */
    @Transactional(readOnly = true)
    public Map<Long, UserProfile> loadProfilesByUserId(Collection<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }

        return userProfileRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile));
    }

    /**
     * Resolves the display name from an already-loaded profile, falling back to the login nickname.
     *
     * @param user    user whose display name is requested
     * @param profile previously loaded profile, may be null
     * @return display name, never null
     */
    public String displayNameOf(User user, UserProfile profile) {
        return profile != null ? profile.getFullName() : user.getUsername();
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String compact = phone.replaceAll("[\\s().-]", "");

        if (!compact.matches("\\+[1-9]\\d{1,14}")) {
            throw new IllegalArgumentException(
                    "Phone must be in international format, e.g. +421904746337");
        }

        return compact;
    }
}

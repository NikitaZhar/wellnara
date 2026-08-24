package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Personal data of a user, kept separate from {@link User}.
 *
 * <p>{@link User} stays focused on authentication (login nickname, email, password, role),
 * while this entity holds human-facing details and is free to grow over time
 * (address, avatar, specialization, …) without bloating the core user.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column
    private String phone;

    /** Optional public WhatsApp contact link (providers only). */
    @Column(name = "whatsapp_url", length = 500)
    private String whatsappUrl;

    /** Optional public Telegram contact link (providers only). */
    @Column(name = "telegram_url", length = 500)
    private String telegramUrl;

    /**
     * Required by JPA.
     */
    protected UserProfile() {
    }

    /**
     * Creates a user profile.
     *
     * @param user      owner of the profile
     * @param firstName user first name
     * @param lastName  user last name
     * @param phone     user phone number, optional (may be null)
     */
    public UserProfile(User user, String firstName, String lastName, String phone) {
        this.user = user;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWhatsappUrl() {
        return whatsappUrl;
    }

    public void setWhatsappUrl(String whatsappUrl) {
        this.whatsappUrl = whatsappUrl;
    }

    public String getTelegramUrl() {
        return telegramUrl;
    }

    public void setTelegramUrl(String telegramUrl) {
        this.telegramUrl = telegramUrl;
    }

    /**
     * Returns the human-facing display name "First Last".
     *
     * @return full name composed of first and last name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}

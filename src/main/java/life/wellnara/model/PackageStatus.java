package life.wellnara.model;

/**
 * Lifecycle of a {@link ServicePackage}.
 *
 * <p>A provider-granted package (an offline-paid gift) is {@link #ACTIVE} from the
 * start. A client-requested package starts {@link #REQUESTED} with its price held
 * on the wallet, and becomes {@link #ACTIVE} when the provider approves it (money
 * settled, sessions granted) or {@link #REJECTED} when the provider declines it
 * (money released). Only {@code ACTIVE} packages cover bookings.
 */
public enum PackageStatus {

    /** Client requested the package; its price is held pending provider approval. */
    REQUESTED,

    /** Package is granted and its sessions can be booked. */
    ACTIVE,

    /** Provider declined the request; the held price was released. */
    REJECTED
}

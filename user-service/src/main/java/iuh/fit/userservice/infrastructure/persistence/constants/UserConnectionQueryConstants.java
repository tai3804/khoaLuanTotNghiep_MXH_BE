package iuh.fit.userservice.infrastructure.persistence.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class UserConnectionQueryConstants {

    public static final String FIND_CONNECTION_BETWEEN = 
            "SELECT c FROM UserConnection c WHERE " +
            "((c.requesterId = :user1 AND c.targetId = :user2) OR (c.requesterId = :user2 AND c.targetId = :user1)) " +
            "AND c.type = :type";

    public static final String FIND_ACCEPTED_FRIENDSHIP = 
            "SELECT c FROM UserConnection c WHERE " +
            "((c.requesterId = :user1 AND c.targetId = :user2) OR (c.requesterId = :user2 AND c.targetId = :user1)) " +
            "AND c.type = 'FRIEND' AND c.status = 'ACCEPTED'";

    public static final String FIND_FRIENDS_OF_USER = 
            "SELECT c FROM UserConnection c WHERE " +
            "(c.requesterId = :userId OR c.targetId = :userId) " +
            "AND c.type = 'FRIEND' AND c.status = 'ACCEPTED'";

    public static final String FIND_FOLLOWERS_OF_USER = 
            "SELECT c FROM UserConnection c WHERE " +
            "c.targetId = :userId AND c.type = 'FOLLOW' AND c.status = 'ACCEPTED'";

    public static final String FIND_FOLLOWING_OF_USER = 
            "SELECT c FROM UserConnection c WHERE " +
            "c.requesterId = :userId AND c.type = 'FOLLOW' AND c.status = 'ACCEPTED'";

    public static final String FIND_PENDING_FRIEND_REQUESTS = 
            "SELECT c FROM UserConnection c WHERE " +
            "c.targetId = :userId AND c.type = 'FRIEND' AND c.status = 'PENDING'";
}

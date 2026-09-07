package iuh.fit.chatservice.application.exception;

import iuh.fit.commonframework.application.exception.BaseError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ChatServiceErrorCode implements BaseError {

    CONVERSATION_NOT_FOUND(404, "Conversation not found", 404),
    NOT_A_CONVERSATION_MEMBER(403, "You are not a member of this conversation", 403),
    NOT_A_GROUP_ADMIN(403, "Only group admins can perform this action", 403),
    GROUP_MEMBER_LIMIT_EXCEEDED(400, "Group member limit exceeded", 400),
    USER_ALREADY_IN_GROUP(400, "User is already a member of this group", 400),
    CANNOT_CHAT_WITH_SELF(400, "Cannot create a direct conversation with yourself", 400),
    INVALID_CONVERSATION_TYPE(400, "Action is not valid for this conversation type", 400),
    MESSAGE_NOT_FOUND(404, "Message not found", 404),
    UNAUTHORIZED_MESSAGE_DELETE(403, "You can only delete your own messages", 403),
    UNAUTHORIZED(401, "Unauthenticated", 401);

    int code;
    String message;
    int statusCode;
}

package iuh.fit.aiservice.presentation.controller;

import iuh.fit.aiservice.application.dto.MessageSummaryDto;
import iuh.fit.aiservice.application.exception.AiServiceErrorCode;
import iuh.fit.aiservice.application.service.PostSummaryService;
import iuh.fit.aiservice.application.service.RagAssistantService;
import iuh.fit.aiservice.application.service.rag.RagContextBuilder;
import iuh.fit.aiservice.infrastructure.gemini.GeminiApiClient;
import iuh.fit.commonframework.application.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Tính năng AI: không trả kết quả mặc định, báo lỗi 400 khi không gọi được Gemini")
class AiAssistantChatTest {

    private GeminiApiClient gemini;
    private RagAssistantService rag;
    private PostSummaryService summary;
    private AiAssistantController controller;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiApiClient.class);
        rag = mock(RagAssistantService.class);
        summary = mock(PostSummaryService.class);
        controller = new AiAssistantController(gemini, summary, rag);
        when(rag.prepare(anyString(), any())).thenAnswer(inv -> new RagContextBuilder.Augmented(inv.getArgument(0), List.of()));
        // Gemini không khả dụng: client trả về đúng giá trị dự phòng được truyền vào
        when(gemini.chatWithHistory(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(gemini.generateText(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(gemini.summarizeMessagesWithGemini(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(summary.generate(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    private static void assertAiError(AiServiceErrorCode expected, Executable call) {
        BusinessException ex = assertThrows(BusinessException.class, call);
        assertEquals(expected, ex.getErrorCode());
        assertEquals(400, ex.getErrorCode().getStatusCode());
    }

    private static AiAssistantController.ChatRequest chat(String message) {
        AiAssistantController.ChatRequest req = new AiAssistantController.ChatRequest();
        req.setMessage(message);
        return req;
    }

    @Test
    @DisplayName("Trợ lý AI: Gemini trả lời được thì trả đúng câu trả lời của mô hình")
    void chatReturnsModelReply() {
        when(gemini.chatWithHistory(anyString(), any(), any())).thenReturn("Câu trả lời từ Gemini");

        var body = controller.chatWithAssistant(chat("Xin chào")).getBody();

        assertNotNull(body);
        assertEquals("Câu trả lời từ Gemini", body.getData().getReply());
    }

    @Test
    @DisplayName("Trợ lý AI: không gọi được Gemini thì lỗi 4002, tin nhắn rỗng thì lỗi 4001")
    void chatErrors() {
        assertAiError(AiServiceErrorCode.AI_ASSISTANT_UNAVAILABLE, () -> controller.chatWithAssistant(chat("Xin chào")));
        assertAiError(AiServiceErrorCode.AI_ASSISTANT_EMPTY_MESSAGE, () -> controller.chatWithAssistant(chat("   ")));
    }

    @Test
    @DisplayName("Gợi ý caption, hashtag, viết lại, dịch, gợi ý trả lời, tiểu sử, phân tích: Gemini lỗi thì 4003")
    void otherFeaturesFailWithoutDefaults() {
        AiAssistantController.CaptionRequest caption = new AiAssistantController.CaptionRequest();
        caption.setTopic("Một ngày học tập");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.suggestCaption(caption));

        AiAssistantController.HashtagRequest hashtag = new AiAssistantController.HashtagRequest();
        hashtag.setContent("Bài viết về lập trình");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.suggestHashtags(hashtag));

        AiAssistantController.EnhanceTextRequest enhance = new AiAssistantController.EnhanceTextRequest();
        enhance.setContent("hom nay di hoc");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.enhanceText(enhance));

        AiAssistantController.TranslateRequest translate = new AiAssistantController.TranslateRequest();
        translate.setContent("Xin chào");
        translate.setTargetLanguage("en");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.translateText(translate));

        AiAssistantController.SummarizeRequest summarize = new AiAssistantController.SummarizeRequest();
        summarize.setContent("Một bài viết dài");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.summarizeText(summarize));

        AiAssistantController.SmartRepliesRequest replies = new AiAssistantController.SmartRepliesRequest();
        replies.setPostContent("Hôm nay trời đẹp");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.suggestReplies(replies));

        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.suggestBio(new AiAssistantController.BioRequest()));

        AiAssistantController.PostAnalysisRequest analyze = new AiAssistantController.PostAnalysisRequest();
        analyze.setContent("Bài viết cần phân tích");
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.analyzePost(analyze));
    }

    @Test
    @DisplayName("Tóm tắt tin nhắn: Gemini lỗi thì 4003, danh sách rỗng thì 4004 (không còn câu tóm tắt soạn sẵn)")
    void summarizeMessagesFailsWithoutDefault() {
        MessageSummaryDto.SummarizeMessagesRequest req = new MessageSummaryDto.SummarizeMessagesRequest();
        MessageSummaryDto.MessageItem item = new MessageSummaryDto.MessageItem();
        item.setSenderName("Trần Tài");
        item.setText("Mai nộp báo cáo nhé");
        req.setMessages(List.of(item));
        assertAiError(AiServiceErrorCode.AI_MODEL_UNAVAILABLE, () -> controller.summarizeMessages(req));

        assertAiError(AiServiceErrorCode.AI_EMPTY_INPUT, () -> controller.summarizeMessages(new MessageSummaryDto.SummarizeMessagesRequest()));
    }

    @Test
    @DisplayName("Gợi ý caption: Gemini trả lời được thì trả về các dòng gợi ý của mô hình")
    void captionReturnsModelLines() {
        when(gemini.generateText(anyString(), any())).thenReturn("1. Dòng một\n- Dòng hai\nDòng ba\nDòng bốn");
        AiAssistantController.CaptionRequest caption = new AiAssistantController.CaptionRequest();
        caption.setTopic("Một ngày học tập");

        var body = controller.suggestCaption(caption).getBody();

        assertNotNull(body);
        assertEquals(List.of("Dòng một", "Dòng hai", "Dòng ba"), body.getData().getSuggestions());
    }
}

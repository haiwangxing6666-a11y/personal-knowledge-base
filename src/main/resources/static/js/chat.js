const CHAT_API = {
    health: "/api/health",
    chat: "/api/chat"
};

const chatState = {
    busy: false,
    toastTimer: null
};

const chatElements = {
    serviceState: document.querySelector("#service-state"),
    serviceStateText: document.querySelector("#service-state-text"),
    messages: document.querySelector("#messages"),
    empty: document.querySelector("#chat-empty"),
    form: document.querySelector("#chat-form"),
    input: document.querySelector("#question-input"),
    count: document.querySelector("#question-count"),
    sendButton: document.querySelector("#send-button"),
    clearButton: document.querySelector("#clear-chat"),
    suggestions: [...document.querySelectorAll("[data-question]")],
    toast: document.querySelector("#toast")
};

function initializeChat() {
    chatElements.form.addEventListener("submit", handleSubmit);
    chatElements.input.addEventListener("input", updateInput);
    chatElements.input.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            chatElements.form.requestSubmit();
        }
    });
    chatElements.clearButton.addEventListener("click", clearConversation);
    chatElements.suggestions.forEach(button => {
        button.addEventListener("click", () => {
            chatElements.input.value = button.dataset.question;
            updateInput();
            chatElements.input.focus();
        });
    });
    checkService();
    chatElements.input.focus();
}

async function handleSubmit(event) {
    event.preventDefault();
    const question = chatElements.input.value.trim();
    if (!question || chatState.busy) return;

    hideEmptyState();
    appendUserMessage(question);
    chatElements.input.value = "";
    updateInput();
    setBusy(true);
    const loadingMessage = appendLoadingMessage();

    try {
        const result = await chatRequest(CHAT_API.chat, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({question})
        });
        loadingMessage.remove();
        appendAnswer(result);
    } catch (error) {
        loadingMessage.remove();
        appendErrorMessage(error.message);
        showChatToast(error.message, "error");
    } finally {
        setBusy(false);
        chatElements.input.focus();
    }
}

function appendUserMessage(question) {
    const message = document.createElement("article");
    message.className = "message user";
    const content = document.createElement("div");
    content.className = "message-content";
    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    bubble.textContent = question;
    content.append(bubble);
    message.append(content);
    chatElements.messages.append(message);
    scrollToLatest();
}

function appendLoadingMessage() {
    const message = createAssistantShell();
    message.classList.add("loading-message");
    const bubble = message.querySelector(".message-bubble");
    bubble.setAttribute("aria-label", "正在检索知识库并生成回答");
    bubble.innerHTML = '<span class="typing"><i></i><i></i><i></i></span>';
    chatElements.messages.append(message);
    scrollToLatest();
    return message;
}

function appendAnswer(result) {
    const message = createAssistantShell();
    if (result.refused) message.classList.add("refused");
    const content = message.querySelector(".message-content");
    const bubble = message.querySelector(".message-bubble");
    bubble.textContent = result.answer || "没有收到有效回答。";

    if (result.secondSearchExecuted || result.rewrittenQuestion) {
        content.append(createRetrievalTrace(result));
    }
    if (Array.isArray(result.sources) && result.sources.length > 0) {
        content.append(createSources(result.sources));
    }

    chatElements.messages.append(message);
    scrollToLatest();
}

function appendErrorMessage(text) {
    const message = createAssistantShell();
    message.classList.add("refused");
    message.querySelector(".message-bubble").textContent = `请求没有完成：${text}`;
    chatElements.messages.append(message);
    scrollToLatest();
}

function createAssistantShell() {
    const message = document.createElement("article");
    message.className = "message assistant";
    const icon = document.createElement("span");
    icon.className = "message-icon";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = "知";
    const content = document.createElement("div");
    content.className = "message-content";
    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    content.append(bubble);
    message.append(icon, content);
    return message;
}

function createRetrievalTrace(result) {
    const trace = document.createElement("div");
    trace.className = "retrieval-trace";
    const status = document.createElement("strong");
    status.textContent = result.secondSearchExecuted ? "已执行二次检索" : "已改写问题";
    trace.append(status);
    if (result.rewrittenQuestion) {
        trace.append(document.createTextNode(` · 检索问题：${result.rewrittenQuestion}`));
    }
    return trace;
}

function createSources(sources) {
    const block = document.createElement("section");
    block.className = "sources-block";
    const title = document.createElement("div");
    title.className = "sources-title";
    title.textContent = `◎ 回答来源 · ${sources.length}`;
    const list = document.createElement("div");
    list.className = "source-list";

    sources.forEach(source => list.append(createSourceCard(source)));
    block.append(title, list);
    return block;
}

function createSourceCard(source) {
    const clickable = isSafeWebUrl(source.sourceUrl);
    const card = document.createElement(clickable ? "a" : "div");
    card.className = "source-card";
    if (clickable) {
        card.href = source.sourceUrl;
        card.target = "_blank";
        card.rel = "noopener noreferrer";
    }
    const name = document.createElement("strong");
    name.textContent = source.documentName || "未知资料";
    const detail = document.createElement("span");
    const indexes = Array.isArray(source.chunkIndexes) && source.chunkIndexes.length
        ? ` · 片段 ${source.chunkIndexes.join(", ")}`
        : "";
    detail.textContent = `${sourceTypeLabel(source.sourceType)}${indexes}${clickable ? " · 打开原文 ↗" : ""}`;
    card.append(name, detail);
    return card;
}

function isSafeWebUrl(value) {
    if (!value) return false;
    try {
        const url = new URL(value);
        return url.protocol === "http:" || url.protocol === "https:";
    } catch {
        return false;
    }
}

function sourceTypeLabel(type) {
    return {
        txt: "TXT 文件",
        md: "Markdown 文件",
        markdown: "Markdown 文件",
        pdf: "PDF 文件",
        docx: "DOCX 文件",
        note: "文本笔记",
        web: "网页资料"
    }[(type || "").toLowerCase()] || type || "知识库资料";
}

function updateInput() {
    chatElements.count.textContent = chatElements.input.value.length;
    chatElements.input.style.height = "auto";
    chatElements.input.style.height = `${Math.min(chatElements.input.scrollHeight, 150)}px`;
}

function setBusy(busy) {
    chatState.busy = busy;
    chatElements.sendButton.disabled = busy;
    chatElements.input.disabled = busy;
    chatElements.sendButton.querySelector("span").textContent = busy ? "思考中" : "发送";
}

function clearConversation() {
    chatElements.messages.querySelectorAll(".message").forEach(message => message.remove());
    chatElements.empty.hidden = false;
    chatElements.input.focus();
}

function hideEmptyState() {
    chatElements.empty.hidden = true;
}

function scrollToLatest() {
    requestAnimationFrame(() => {
        chatElements.messages.scrollTop = chatElements.messages.scrollHeight;
    });
}

async function checkService() {
    try {
        await chatRequest(CHAT_API.health);
        chatElements.serviceState.className = "service-state online";
        chatElements.serviceStateText.textContent = "服务正常";
    } catch {
        chatElements.serviceState.className = "service-state offline";
        chatElements.serviceStateText.textContent = "连接失败";
    }
}

async function chatRequest(url, options = {}) {
    let response;
    try {
        response = await fetch(url, options);
    } catch {
        throw new Error("无法连接服务器，请确认应用已经启动");
    }
    if (!response.ok) {
        let body;
        try {
            body = await response.json();
        } catch {
            throw new Error(`请求失败（HTTP ${response.status}）`);
        }
        const fields = body.fieldErrors
            ? Object.values(body.fieldErrors).filter(Boolean).join("；")
            : "";
        throw new Error(fields || body.message || `请求失败（HTTP ${response.status}）`);
    }
    return response.json();
}

function showChatToast(message, type) {
    clearTimeout(chatState.toastTimer);
    chatElements.toast.textContent = message;
    chatElements.toast.className = `toast ${type} visible`;
    chatState.toastTimer = setTimeout(() => {
        chatElements.toast.classList.remove("visible");
    }, 3600);
}

document.addEventListener("DOMContentLoaded", initializeChat);

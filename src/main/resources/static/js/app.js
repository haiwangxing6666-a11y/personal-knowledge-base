const API = {
    health: "/api/health",
    documents: "/api/documents",
    notes: "/api/documents/notes",
    links: "/api/documents/links"
};

const state = {
    documents: [],
    pendingDelete: null,
    pendingEdit: null,
    toastTimer: null
};

const elements = {
    serviceState: document.querySelector("#service-state"),
    serviceStateText: document.querySelector("#service-state-text"),
    totalCount: document.querySelector("#total-count"),
    readyCount: document.querySelector("#ready-count"),
    chunkCount: document.querySelector("#chunk-count"),
    tabs: [...document.querySelectorAll(".composer-tab")],
    panels: [...document.querySelectorAll(".tab-panel")],
    fileForm: document.querySelector("#file-form"),
    fileInput: document.querySelector("#file-input"),
    fileLabel: document.querySelector("#file-label"),
    dropZone: document.querySelector("#drop-zone"),
    noteForm: document.querySelector("#note-form"),
    linkForm: document.querySelector("#link-form"),
    refreshButton: document.querySelector("#refresh-button"),
    loadingState: document.querySelector("#loading-state"),
    emptyState: document.querySelector("#empty-state"),
    documentList: document.querySelector("#document-list"),
    deleteDialog: document.querySelector("#delete-dialog"),
    deleteName: document.querySelector("#delete-name"),
    confirmDelete: document.querySelector("#confirm-delete"),
    editNoteDialog: document.querySelector("#edit-note-dialog"),
    editNoteForm: document.querySelector("#edit-note-form"),
    editNoteTitle: document.querySelector("#edit-note-title"),
    editNoteContent: document.querySelector("#edit-note-content"),
    editNoteHint: document.querySelector("#edit-note-hint"),
    cancelNoteEdit: document.querySelector("#cancel-note-edit"),
    saveNoteEdit: document.querySelector("#save-note-edit"),
    toast: document.querySelector("#toast")
};

const TYPE_INFO = {
    txt: {label: "TXT", color: "#287f96"},
    md: {label: "MARKDOWN", color: "#765aa3"},
    markdown: {label: "MARKDOWN", color: "#765aa3"},
    pdf: {label: "PDF", color: "#c45151"},
    docx: {label: "DOCX", color: "#3f6fa8"},
    note: {label: "笔记", color: "#b27828"},
    web: {label: "网页", color: "#258269"}
};

function init() {
    bindTabs();
    bindForms();
    bindFileDrop();
    bindDeleteDialog();
    bindNoteEditor();
    elements.refreshButton.addEventListener("click", loadDocuments);
    loadHealth();
    loadDocuments();
}

function bindNoteEditor() {
    elements.cancelNoteEdit.addEventListener("click", () => {
        elements.editNoteDialog.close();
    });

    elements.editNoteDialog.addEventListener("close", () => {
        state.pendingEdit = null;
        elements.editNoteForm.reset();
    });

    elements.editNoteForm.addEventListener("submit", async event => {
        event.preventDefault();
        if (!state.pendingEdit) return;

        const note = state.pendingEdit;
        const data = new FormData(elements.editNoteForm);
        try {
            elements.saveNoteEdit.disabled = true;
            elements.saveNoteEdit.textContent = "保存中……";
            await request(`${API.documents}/${note.id}`, {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    name: data.get("name"),
                    content: data.get("content")
                })
            });
            elements.editNoteDialog.close();
            showToast("笔记已更新并重新生成向量索引", "success");
            await loadDocuments();
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            elements.saveNoteEdit.disabled = false;
            elements.saveNoteEdit.textContent = "保存修改";
        }
    });
}

function bindTabs() {
    elements.tabs.forEach(tab => {
        tab.addEventListener("click", () => {
            const selected = tab.dataset.tab;
            elements.tabs.forEach(candidate => {
                const active = candidate === tab;
                candidate.classList.toggle("active", active);
                candidate.setAttribute("aria-selected", String(active));
            });
            elements.panels.forEach(panel => {
                const active = panel.id === `panel-${selected}`;
                panel.classList.toggle("active", active);
                panel.hidden = !active;
            });
        });
    });
}

function bindForms() {
    elements.fileForm.addEventListener("submit", async event => {
        event.preventDefault();
        const file = elements.fileInput.files[0];
        if (!file) {
            showToast("请先选择一个文件", "error");
            return;
        }
        const body = new FormData();
        body.append("file", file);
        await submitForm(elements.fileForm, () => request(API.documents, {
            method: "POST",
            body
        }), "文件已上传并完成入库");
        elements.fileInput.value = "";
        elements.fileLabel.textContent = "拖放文件到这里，或点击选择";
    });

    elements.noteForm.addEventListener("submit", async event => {
        event.preventDefault();
        const data = new FormData(elements.noteForm);
        await submitForm(elements.noteForm, () => request(API.notes, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                title: data.get("title"),
                content: data.get("content")
            })
        }), "笔记已保存到知识库");
    });

    elements.linkForm.addEventListener("submit", async event => {
        event.preventDefault();
        const data = new FormData(elements.linkForm);
        await submitForm(elements.linkForm, () => request(API.links, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                url: data.get("url"),
                title: data.get("title") || null
            })
        }), "网页已抓取并收藏");
    });
}

function bindFileDrop() {
    elements.fileInput.addEventListener("change", () => {
        const file = elements.fileInput.files[0];
        elements.fileLabel.textContent = file ? file.name : "拖放文件到这里，或点击选择";
    });

    ["dragenter", "dragover"].forEach(type => {
        elements.dropZone.addEventListener(type, event => {
            event.preventDefault();
            elements.dropZone.classList.add("dragging");
        });
    });
    ["dragleave", "drop"].forEach(type => {
        elements.dropZone.addEventListener(type, event => {
            event.preventDefault();
            elements.dropZone.classList.remove("dragging");
        });
    });
    elements.dropZone.addEventListener("drop", event => {
        const file = event.dataTransfer.files[0];
        if (!file) return;
        const transfer = new DataTransfer();
        transfer.items.add(file);
        elements.fileInput.files = transfer.files;
        elements.fileLabel.textContent = file.name;
    });
}

function bindDeleteDialog() {
    elements.deleteDialog.addEventListener("close", async () => {
        if (elements.deleteDialog.returnValue !== "confirm" || !state.pendingDelete) {
            state.pendingDelete = null;
            return;
        }
        const documentToDelete = state.pendingDelete;
        state.pendingDelete = null;
        try {
            elements.confirmDelete.disabled = true;
            await request(`${API.documents}/${documentToDelete.id}`, {method: "DELETE"});
            showToast(`已删除“${documentToDelete.name}”`, "success");
            await loadDocuments();
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            elements.confirmDelete.disabled = false;
        }
    });
}

async function submitForm(form, action, successMessage) {
    const button = form.querySelector("button[type='submit']");
    const originalText = button.textContent;
    try {
        button.disabled = true;
        button.textContent = "处理中……";
        await action();
        form.reset();
        showToast(successMessage, "success");
        await loadDocuments();
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        button.disabled = false;
        button.textContent = originalText;
    }
}

async function loadHealth() {
    try {
        await request(API.health);
        elements.serviceState.className = "service-state online";
        elements.serviceStateText.textContent = "服务正常";
    } catch {
        elements.serviceState.className = "service-state offline";
        elements.serviceStateText.textContent = "连接失败";
    }
}

async function loadDocuments() {
    setLibraryLoading(true);
    try {
        state.documents = await request(API.documents);
        renderDocuments();
    } catch (error) {
        state.documents = [];
        renderDocuments();
        showToast(error.message, "error");
    } finally {
        setLibraryLoading(false);
    }
}

function renderDocuments() {
    updateStats();
    elements.documentList.innerHTML = "";
    elements.emptyState.hidden = state.documents.length !== 0;

    for (const item of state.documents) {
        const type = TYPE_INFO[(item.fileType || "").toLowerCase()] || {
            label: item.fileType || "资料",
            color: "#60758a"
        };
        const card = document.createElement("article");
        card.className = "document-card";
        card.style.setProperty("--type-color", type.color);
        const editable = (item.fileType || "").toLowerCase() === "note";
        card.innerHTML = `
            <div class="document-card-head">
                <span class="type-badge">${escapeHtml(type.label)}</span>
                <div class="document-actions">
                    ${editable ? `<button class="edit-button" type="button" aria-label="修改 ${escapeHtml(item.name)}" title="修改笔记">✎</button>` : ""}
                    <button class="delete-button" type="button" aria-label="删除 ${escapeHtml(item.name)}" title="删除资料">×</button>
                </div>
            </div>
            <h3 title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</h3>
            ${item.sourceUrl ? `<p class="document-url" title="${escapeHtml(item.sourceUrl)}">${escapeHtml(item.sourceUrl)}</p>` : ""}
            <div class="document-meta">
                <span class="ready-pill">${statusLabel(item.status)}</span>
                <span>${Number(item.chunkCount || 0)} 个片段</span>
                <time>${formatDate(item.uploadTime)}</time>
            </div>
        `;
        const editButton = card.querySelector(".edit-button");
        if (editButton) {
            editButton.addEventListener("click", () => openNoteEditor(item));
        }
        card.querySelector(".delete-button").addEventListener("click", () => openDeleteDialog(item));
        elements.documentList.append(card);
    }
}

async function openNoteEditor(item) {
    try {
        const detail = await request(`${API.documents}/${item.id}`);
        state.pendingEdit = detail;
        elements.editNoteTitle.value = detail.name || "";
        elements.editNoteContent.value = detail.content || "";
        elements.editNoteHint.textContent = detail.content == null
            ? "这条笔记创建于正文存储功能上线前，请重新输入完整正文。"
            : "保存后会重新切分正文并更新向量索引。";
        elements.editNoteDialog.showModal();
    } catch (error) {
        showToast(error.message, "error");
    }
}

function updateStats() {
    elements.totalCount.textContent = state.documents.length;
    elements.readyCount.textContent = state.documents.filter(item => item.status === "READY").length;
    elements.chunkCount.textContent = state.documents.reduce(
        (total, item) => total + Number(item.chunkCount || 0),
        0
    );
}

function openDeleteDialog(item) {
    state.pendingDelete = item;
    elements.deleteName.textContent = item.name;
    elements.deleteDialog.showModal();
}

function setLibraryLoading(loading) {
    elements.loadingState.hidden = !loading;
    elements.refreshButton.disabled = loading;
    if (loading) {
        elements.emptyState.hidden = true;
        elements.documentList.innerHTML = "";
    }
}

async function request(url, options = {}) {
    let response;
    try {
        response = await fetch(url, options);
    } catch {
        throw new Error("无法连接服务器，请确认应用已经启动");
    }

    if (!response.ok) {
        let errorBody;
        try {
            errorBody = await response.json();
        } catch {
            throw new Error(`请求失败（HTTP ${response.status}）`);
        }
        const fieldMessage = errorBody.fieldErrors
            ? Object.values(errorBody.fieldErrors).filter(Boolean).join("；")
            : "";
        throw new Error(fieldMessage || errorBody.message || `请求失败（HTTP ${response.status}）`);
    }

    if (response.status === 204) return null;
    return response.json();
}

function showToast(message, type = "success") {
    clearTimeout(state.toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast ${type} visible`;
    state.toastTimer = setTimeout(() => {
        elements.toast.classList.remove("visible");
    }, 3600);
}

function statusLabel(status) {
    return {
        READY: "索引完成",
        PENDING: "等待处理",
        FAILED: "处理失败"
    }[status] || status || "未知状态";
}

function formatDate(value) {
    if (!value) return "时间未知";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

document.addEventListener("DOMContentLoaded", init);

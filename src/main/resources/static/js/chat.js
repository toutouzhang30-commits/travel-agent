let sessionId = localStorage.getItem('travel_session_id') || '';
const messageList = document.getElementById('message-list');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');
const statusArea = document.getElementById('status-area');
const itineraryDisplay = document.getElementById('itinerary-display');
const eventLogArea = document.getElementById('event-log-area');
const retrievalArea = document.getElementById('retrieval-area');
const toolArea = document.getElementById('tool-area');
const sourceArea = document.getElementById('source-area');

document.addEventListener('DOMContentLoaded', () => {
    eventLogArea.innerText = '等待执行步骤...';
    retrievalArea.innerText = '尚未触发知识检索...';
    toolArea.innerText = '尚未触发工具调用...';
    sourceArea.innerText = '将在此展示知识库来源和实时工具来源...';

    sendBtn.addEventListener('click', sendMessage);
    userInput.addEventListener('keypress', (event) => {
        if (event.key === 'Enter') {
            sendMessage();
        }
    });
});

async function sendMessage() {
    const message = userInput.value.trim();
    if (!message) return;

    const originalInput = message;
    prepareForSending(message);

    try {
        const response = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message, sessionId })
        });

        if (!response.ok) {
            throw new Error('服务器响应异常');
        }

        if (!response.body) {
            throw new Error('服务器没有返回可读取的流式响应');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { value, done } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split(/\r?\n\r?\n/);
            buffer = parts.pop() || '';
            parts.forEach(parseSseBlock);
        }

        if (buffer.trim()) {
            parseSseBlock(buffer);
        }
    } catch (error) {
        userInput.value = originalInput;
        appendMessage('ai', '操作失败: ' + error.message);
    } finally {
        finishSending();
    }
}

function parseSseBlock(block) {
    const dataLines = block
        .split(/\r?\n/)
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).trim());

    if (dataLines.length === 0) {
        return;
    }

    const jsonStr = dataLines.join('\n');
    try {
        const event = JSON.parse(jsonStr);
        handleAgentEvent(event);
    } catch (error) {
        console.error('JSON 解析错误:', error, jsonStr);
    }
}

function handleAgentEvent(event) {
    if (event.sessionId && !sessionId) {
        sessionId = event.sessionId;
        localStorage.setItem('travel_session_id', sessionId);
    }

    switch (event.type) {
        case 'STATUS':
            statusArea.innerText = '⚙️ ' + event.message;
            appendEventLog('状态', event.message);
            updateSidePanelsFromStatus(event.message);
            break;
        case 'RETRIEVAL':
            appendEventLog('知识库', event.message || summarizeRetrieval(event));
            renderRetrieval(event);
            renderSources(event.payload?.sources);
            break;
        case 'TOOL_CALL':
            appendEventLog('工具调用', summarizeToolCall(event.toolCallPayload, event.message));
            renderToolCall(event.toolCallPayload);
            break;
        case 'TOOL_RESULT':
            appendEventLog('工具结果', summarizeToolResult(event.toolResultPayload, event.message));
            renderToolResult(event.toolResultPayload);
            if (event.toolResultPayload?.source) {
                renderSources([event.toolResultPayload.source]);
            }
            break;
        case 'ANSWER':
            appendEventLog('回答', '已生成回答');
            appendOrUpdateAiMessage(buildAnswerText(event));
            renderSources(event.payload?.sources);
            break;
        case 'ITINERARY':
            appendEventLog('行程', '已生成或更新行程');
            if (event.message) {
                appendOrUpdateAiMessage(event.message);
            }
            renderItineraryCard(event.payload?.itinerary);
            renderSources(event.payload?.sources);
            break;
        case 'DONE':
            statusArea.innerText = '✅ 处理完成';
            appendEventLog('完成', '本轮处理完成');
            break;
        case 'ERROR':
            appendEventLog('错误', event.message || '处理失败');
            appendMessage('ai', '提醒: ' + event.message);
            break;
    }
}

function appendEventLog(label, message) {
    if (!eventLogArea) {
        return;
    }

    const normalizedMessage = message || '已收到执行事件';
    if (eventLogArea.dataset.empty !== 'false') {
        eventLogArea.innerHTML = '';
        eventLogArea.dataset.empty = 'false';
    }

    const item = document.createElement('div');
    item.className = 'event-log-item';

    const time = document.createElement('span');
    time.className = 'event-log-time';
    time.innerText = new Date().toLocaleTimeString('zh-CN', { hour12: false });

    const labelSpan = document.createElement('span');
    labelSpan.className = 'event-log-label';
    labelSpan.innerText = label;

    const messageSpan = document.createElement('span');
    messageSpan.innerText = normalizedMessage;

    item.appendChild(time);
    item.appendChild(labelSpan);
    item.appendChild(messageSpan);
    eventLogArea.appendChild(item);
    eventLogArea.scrollTop = eventLogArea.scrollHeight;
}

function updateSidePanelsFromStatus(message) {
    if (!message) {
        return;
    }

    if (message.includes('检索') || message.includes('知识库') || message.includes('RAG')) {
        retrievalArea.innerText = message;
    }

    if (message.includes('工具') || message.includes('天气') || message.includes('票价')
            || message.includes('路线') || message.includes('地图') || message.includes('耗时')
            || message.includes('距离')) {
        toolArea.innerText = message;
    }
}

function summarizeRetrieval(event) {
    const sources = Array.isArray(event.payload?.sources) ? event.payload.sources : [];
    return sources.length > 0
        ? `知识库检索完成，命中 ${sources.length} 条来源`
        : '已执行知识库检索，暂无可展示来源';
}

function summarizeToolCall(payload, fallbackMessage) {
    if (!payload) {
        return fallbackMessage || '正在调用工具';
    }

    return `${formatToolName(payload.toolName)}：${payload.summary || fallbackMessage || '正在调用'}`;
}

function summarizeToolResult(payload, fallbackMessage) {
    if (!payload) {
        return fallbackMessage || '工具调用已返回';
    }

    if (!payload.success) {
        return `${formatToolName(payload.toolName)} 调用失败：${payload.errorMessage || payload.summary || fallbackMessage || '未提供失败原因'}`;
    }

    return `${formatToolName(payload.toolName)} 调用完成：${payload.summary || fallbackMessage || '已返回结果'}`;
}

function renderRetrieval(event) {
    const payload = event.payload;
    const sources = Array.isArray(payload?.sources) ? payload.sources : [];

    if (sources.length === 0) {
        retrievalArea.innerText = event.message || '已触发知识检索，但暂无来源信息';
        return;
    }

    const citySummary = [...new Set(sources.map(source => source.city).filter(Boolean))].join('、');
    const recallSummary = summarizeRecallSources(sources);
    retrievalArea.innerText = `${event.message || '已触发知识检索'}${citySummary ? `（城市：${citySummary}）` : ''}，命中 ${sources.length} 条来源${recallSummary ? `，召回通道：${recallSummary}` : ''}`;
}

function renderToolCall(payload) {
    if (!payload) {
        return;
    }

    toolArea.innerHTML = buildToolPanelHtml({
        state: '调用中',
        stateClass: 'running',
        toolName: payload.toolName,
        summary: payload.summary || '正在调用实时工具',
        timeLabel: '请求时间',
        timeValue: payload.requestedAt
    });
}

function renderToolResult(payload) {
    if (!payload) {
        return;
    }

    if (!payload.success) {
        toolArea.innerHTML = buildToolPanelHtml({
            state: '失败',
            stateClass: 'failed',
            toolName: payload.toolName,
            summary: payload.errorMessage || payload.summary || '工具调用失败',
            timeLabel: '更新时间',
            timeValue: payload.updatedAt
        });
        return;
    }

    toolArea.innerHTML = buildToolPanelHtml({
        state: '完成',
        stateClass: 'success',
        toolName: payload.toolName,
        summary: payload.summary || '工具已返回结果',
        timeLabel: '更新时间',
        timeValue: payload.updatedAt
    });
}

function buildToolPanelHtml({ state, stateClass, toolName, summary, timeLabel, timeValue }) {
    const displayName = formatToolName(toolName);
    const description = describeTool(toolName);

    return `
        <div class="tool-card tool-card-${escapeHtml(stateClass)}">
            <div class="tool-card-header">
                <span class="tool-card-state">${escapeHtml(state)}</span>
                <strong>${escapeHtml(displayName)}</strong>
            </div>
            <div class="tool-card-summary">${escapeHtml(summary)}</div>
            <div class="tool-card-meta">${escapeHtml(description)}</div>
            ${timeValue ? `<div class="tool-card-meta">${escapeHtml(timeLabel)}：${escapeHtml(timeValue)}</div>` : ''}
        </div>
    `;
}

function renderSources(sources) {
    if (!Array.isArray(sources) || sources.length === 0) {
        return;
    }

    const html = sources.map((source, index) => {
        const sourceType = source.sourceType || '未知类型';
        const city = source.city || '未知城市';
        const topic = source.topic || '未标注主题';
        const sourceName = source.sourceName || '未标注来源';
        const sourceUrl = source.sourceUrl;
        const summary = source.summary || '暂无摘要';
        const toolName = source.toolName || '';
        const effectiveAt = source.effectiveAt || source.verifiedAt || '未标注时间';
        const recallSources = Array.isArray(source.recallSources) ? source.recallSources : [];
        const scoreSource = source.scoreSource || '';
        const badges = buildSourceBadges(sourceType, recallSources, scoreSource);
        const scoreLine = buildScoreLine(source);

        const sourceLine = sourceUrl
            ? `<a href="${escapeHtml(sourceUrl)}" target="_blank" rel="noreferrer">${escapeHtml(sourceName)}</a>`
            : escapeHtml(sourceName);

        const title = sourceType === 'TOOL'
            ? `${index + 1}. ${city} / ${formatToolName(toolName) || topic}`
            : `${index + 1}. ${city} / ${topic}`;

        return `
            <div style="padding: 8px 0; border-bottom: 1px dashed #e5e5e5;">
                <div><strong>${escapeHtml(title)}</strong></div>
                ${badges ? `<div style="margin: 6px 0;">${badges}</div>` : ''}
                <div>类型：${escapeHtml(formatSourceType(sourceType))}</div>
                <div>来源：${sourceLine}</div>
                <div>时间：${escapeHtml(effectiveAt)}</div>
                ${scoreLine ? `<div>评分：${scoreLine}</div>` : ''}
                <div>摘要：${escapeHtml(summary)}</div>
            </div>
        `;
    }).join('');

    sourceArea.innerHTML = html;
}

function summarizeRecallSources(sources) {
    const labels = new Set();
    sources.forEach(source => {
        const recallSources = Array.isArray(source.recallSources) ? source.recallSources : [];
        recallSources.forEach(recallSource => labels.add(formatRecallSource(recallSource)));
        if (source.scoreSource) {
            labels.add(formatScoreSource(source.scoreSource));
        }
    });

    return [...labels].filter(Boolean).join(' + ');
}

function buildSourceBadges(sourceType, recallSources, scoreSource) {
    const labels = [];
    if (sourceType) {
        labels.push(formatSourceType(sourceType));
    }
    recallSources.forEach(recallSource => labels.push(formatRecallSource(recallSource)));
    if (scoreSource) {
        labels.push(formatScoreSource(scoreSource));
    }

    return [...new Set(labels.filter(Boolean))]
        .map(label => `<span class="source-badge">${escapeHtml(label)}</span>`)
        .join('');
}

function formatSourceType(sourceType) {
    switch (sourceType) {
        case 'TOOL':
            return '实时工具';
        case 'RAG':
            return '知识库';
        default:
            return sourceType || '';
    }
}

function formatToolName(toolName) {
    switch (toolName) {
        case 'WeatherTool':
            return '天气工具';
        case 'MapsTool':
            return '地图工具';
        case 'PricingTool':
            return '票价工具';
        default:
            return toolName || '未知工具';
    }
}

function describeTool(toolName) {
    switch (toolName) {
        case 'WeatherTool':
            return '用于查询实时天气、温度、降雨等信息';
        case 'MapsTool':
            return '用于查询路线、距离和交通耗时，来源为地图服务';
        case 'PricingTool':
            return '用于查询实时票价、门票或余票状态';
        default:
            return '实时工具调用结果';
    }
}

function formatRecallSource(source) {
    switch (source) {
        case 'VECTOR':
            return 'VECTOR';
        case 'LEXICAL':
            return 'BM25';
        default:
            return source || '';
    }
}

function formatScoreSource(scoreSource) {
    switch (scoreSource) {
        case 'DASHSCOPE_RERANK':
            return 'RERANK';
        case 'HYBRID_UNION':
            return 'HYBRID';
        default:
            return scoreSource || '';
    }
}

function buildScoreLine(source) {
    const parts = [];
    appendScorePart(parts, 'final', source.score);
    appendScorePart(parts, 'vector', source.vectorScore);
    appendScorePart(parts, 'bm25', source.lexicalScore);
    appendScorePart(parts, 'rerank', source.rerankScore);
    return parts.join(' / ');
}

function appendScorePart(parts, label, value) {
    if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
        return;
    }
    parts.push(`${label} ${value.toFixed(3)}`);
}

function buildAnswerText(event) {
    const payload = event.payload;
    if (!payload) {
        return event.message;
    }

    const followUpQuestion = payload.followUpQuestion?.trim();
    const missingFields = Array.isArray(payload.missingFields) ? payload.missingFields : [];
    if (!followUpQuestion && missingFields.length === 0) {
        return event.message;
    }

    const lines = [];
    if (followUpQuestion) {
        lines.push(followUpQuestion);
    } else if (event.message) {
        lines.push(event.message);
    }

    if (missingFields.length > 0) {
        lines.push('当前还需要：' + missingFields.map(formatMissingFieldLabel).join('、'));
    }

    return lines.join('\n');
}

function formatMissingFieldLabel(field) {
    switch (field) {
        case 'destination':
            return '目的地';
        case 'tripDays':
            return '旅行天数';
        case 'budget':
            return '预算';
        case 'preference':
            return '节奏或兴趣偏好';
        default:
            return field;
    }
}

function prepareForSending(text) {
    appendMessage('user', text);
    userInput.value = '';
    userInput.disabled = true;
    sendBtn.disabled = true;
    sendBtn.innerText = '发送中...';
    statusArea.innerText = '⏳ AI 正在思考并编排任务...';
    eventLogArea.innerHTML = '';
    eventLogArea.dataset.empty = 'false';
    appendEventLog('开始', '已发送消息，等待后端编排');
    retrievalArea.innerText = '尚未触发知识检索...';
    toolArea.innerText = '尚未触发工具调用...';
    sourceArea.innerText = '等待本轮结果中的来源信息...';
}

function finishSending() {
    userInput.disabled = false;
    sendBtn.disabled = false;
    sendBtn.innerText = '发送';
    userInput.focus();
}

function appendMessage(role, text) {
    const div = document.createElement('div');
    div.className = `message ${role}-msg`;
    div.innerText = text;
    messageList.appendChild(div);
    messageList.scrollTop = messageList.scrollHeight;
}

function appendOrUpdateAiMessage(text) {
    const lastMessage = messageList.lastElementChild;
    if (lastMessage && lastMessage.classList.contains('ai-msg')) {
        lastMessage.innerText = text;
    } else {
        appendMessage('ai', text);
    }
}

function renderItineraryCard(itinerary) {
    if (!itinerary) return;

    let html = `
        <div style="background: white; border-radius: 8px; padding: 15px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
            <h2 style="margin-top: 0; color: #1a73e8;">${itinerary.destination}</h2>
            <p><strong>行程天数：</strong>${itinerary.tripDays} 天</p>
            <hr/>
    `;

    itinerary.days.forEach((day) => {
        html += `
            <div style="margin-bottom: 20px; border-left: 4px solid #1a73e8; padding-left: 15px;">
                <h4 style="margin: 10px 0;">第 ${day.dayNumber} 天</h4>
                ${renderSlot('上午', day.morning)}
                ${renderSlot('下午', day.afternoon)}
                ${renderSlot('晚上', day.evening)}
            </div>
        `;
    });

    html += '</div>';
    itineraryDisplay.innerHTML = html;
    itineraryDisplay.scrollTop = 0;
}

function renderSlot(timeLabel, slot) {
    if (!slot) return '';

    return `
        <div style="margin-bottom: 10px; font-size: 0.95em;">
            <div style="font-weight: bold; color: #555;">${timeLabel}：${slot.activityName}</div>
            <div style="color: #666; font-size: 0.85em; margin-top: 2px;">推荐理由：${slot.reason}</div>
            <div style="color: #1a73e8; font-size: 0.85em; margin-top: 2px;">预算提示：${slot.budgetNote}</div>
        </div>
    `;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

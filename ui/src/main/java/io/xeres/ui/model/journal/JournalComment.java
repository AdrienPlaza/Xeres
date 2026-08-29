/*
 * Copyright (c) 2025-2026 by David Gerber - https://zapek.com
 *
 * This file is part of Xeres.
 *
 * Xeres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeres.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.xeres.ui.model.journal;

import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;

import java.time.Instant;

public class JournalComment
{
	private long articleMessageId;
	private MsgId articleMsgId;
	private GxsId authorGxsId;
	private String authorName;
	private String content;
	private Instant published;

	public JournalComment()
	{
	}

	public long getArticleMessageId()
	{
		return articleMessageId;
	}

	public void setArticleMessageId(long articleMessageId)
	{
		this.articleMessageId = articleMessageId;
	}

	public MsgId getArticleMsgId()
	{
		return articleMsgId;
	}

	public void setArticleMsgId(MsgId articleMsgId)
	{
		this.articleMsgId = articleMsgId;
	}

	public GxsId getAuthorGxsId()
	{
		return authorGxsId;
	}

	public void setAuthorGxsId(GxsId authorGxsId)
	{
		this.authorGxsId = authorGxsId;
	}

	public String getAuthorName()
	{
		return authorName;
	}

	public void setAuthorName(String authorName)
	{
		this.authorName = authorName;
	}

	public String getContent()
	{
		return content;
	}

	public void setContent(String content)
	{
		this.content = content;
	}

	public Instant getPublished()
	{
		return published;
	}

	public void setPublished(Instant published)
	{
		this.published = published;
	}
}

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
import io.xeres.ui.controller.common.GxsMessage;

import java.time.Instant;
import java.util.Objects;

public class JournalMessage implements GxsMessage
{
	private long id;
	private GxsId gxsId;
	private GxsId groupGxsId;
	private MsgId msgId;
	private GxsId authorGxsId;
	private String authorName;
	private String groupName;
	private String title;
	private String content;
	private boolean hasImage;
	private int imageWidth;
	private int imageHeight;
	private Instant published;
	private String snippet;
	private int likeCount;
	private int dislikeCount;
	private String myVote;
	private int commentCount;
	private boolean favorited;
	private boolean read;
	private boolean selected; // For UI purposes only

	public JournalMessage()
	{
	}

	@Override
	public long getId()
	{
		return id;
	}

	public void setId(long id)
	{
		this.id = id;
	}

	@Override
	public GxsId getGxsId()
	{
		return gxsId;
	}

	public void setGxsId(GxsId gxsId)
	{
		this.gxsId = gxsId;
	}

	public GxsId getGroupGxsId()
	{
		return groupGxsId;
	}

	public void setGroupGxsId(GxsId groupGxsId)
	{
		this.groupGxsId = groupGxsId;
	}

	public MsgId getMsgId()
	{
		return msgId;
	}

	public void setMsgId(MsgId msgId)
	{
		this.msgId = msgId;
	}

	@Override
	public long getOriginalId()
	{
		return id;
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

	public String getGroupName()
	{
		return groupName;
	}

	public void setGroupName(String groupName)
	{
		this.groupName = groupName;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	public String getContent()
	{
		return content;
	}

	public void setContent(String content)
	{
		this.content = content;
	}

	public boolean hasImage()
	{
		return hasImage;
	}

	public void setHasImage(boolean hasImage)
	{
		this.hasImage = hasImage;
	}

	public int getImageWidth()
	{
		return imageWidth;
	}

	public void setImageWidth(int imageWidth)
	{
		this.imageWidth = imageWidth;
	}

	public int getImageHeight()
	{
		return imageHeight;
	}

	public void setImageHeight(int imageHeight)
	{
		this.imageHeight = imageHeight;
	}

	@Override
	public Instant getPublished()
	{
		return published;
	}

	public void setPublished(Instant published)
	{
		this.published = published;
	}

	public String getSnippet()
	{
		return snippet;
	}

	public void setSnippet(String snippet)
	{
		this.snippet = snippet;
	}

	public int getLikeCount()
	{
		return likeCount;
	}

	public void setLikeCount(int likeCount)
	{
		this.likeCount = likeCount;
	}

	public int getDislikeCount()
	{
		return dislikeCount;
	}

	public void setDislikeCount(int dislikeCount)
	{
		this.dislikeCount = dislikeCount;
	}

	public String getMyVote()
	{
		return myVote;
	}

	public void setMyVote(String myVote)
	{
		this.myVote = myVote;
	}

	public int getCommentCount()
	{
		return commentCount;
	}

	public void setCommentCount(int commentCount)
	{
		this.commentCount = commentCount;
	}

	public boolean isFavorited()
	{
		return favorited;
	}

	public void setFavorited(boolean favorited)
	{
		this.favorited = favorited;
	}

	@Override
	public boolean isRead()
	{
		return read;
	}

	@Override
	public void setRead(boolean read)
	{
		this.read = read;
	}

	public boolean isSelected()
	{
		return selected;
	}

	public void setSelected(boolean selected)
	{
		this.selected = selected;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof JournalMessage that))
		{
			return false;
		}
		return id == that.id;
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(id);
	}
}

/*
 * Copyright (c) 2026 by David Gerber - https://zapek.com
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

package io.xeres.app.xrs.service.journal.item;

import io.netty.buffer.ByteBuf;
import io.xeres.app.database.model.gxs.GxsMessageItem;
import io.xeres.app.xrs.serialization.SerializationFlags;
import io.xeres.app.xrs.serialization.TlvSerializer;
import io.xeres.app.xrs.serialization.TlvType;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import io.xeres.common.util.ByteUnitUtils;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Set;

import static io.xeres.app.xrs.serialization.TlvType.STR_MSG;

@Entity(name = "journal_message")
public class JournalMessageItem extends GxsMessageItem
{
	@SuppressWarnings("unused")
	public static final JournalMessageItem EMPTY = new JournalMessageItem();

	private String title;

	private String content;

	private byte[] image;

	private int imageWidth;

	private int imageHeight;

	private boolean read;

	public JournalMessageItem()
	{
		// Needed for JPA
	}

	public JournalMessageItem(GxsId gxsId, MsgId msgId, String name)
	{
		setGxsId(gxsId);
		setMsgId(msgId);
		setName(name);
		updatePublished();
	}

	@Override
	public int getSubType()
	{
		return 3;
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
		return image != null;
	}

	public byte[] getImage()
	{
		return image;
	}

	public void setImage(byte[] image)
	{
		if (ArrayUtils.isNotEmpty(image))
		{
			this.image = image;
		}
		else
		{
			this.image = null;
		}
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

	public boolean isRead()
	{
		return read;
	}

	public void setRead(boolean read)
	{
		this.read = read;
	}

	@Override
	public int writeDataObject(ByteBuf buf, Set<SerializationFlags> serializationFlags)
	{
		var size = 0;

		size += TlvSerializer.serialize(buf, TlvType.STR_TITLE, title);
		size += TlvSerializer.serialize(buf, STR_MSG, content);
		size += TlvSerializer.serialize(buf, TlvType.IMAGE, image);
		return size;
	}

	@Override
	public void readDataObject(ByteBuf buf)
	{
		title = (String) TlvSerializer.deserialize(buf, TlvType.STR_TITLE);
		content = (String) TlvSerializer.deserialize(buf, STR_MSG);
		setImage((byte[]) TlvSerializer.deserialize(buf, TlvType.IMAGE));
	}

	@Override
	public JournalMessageItem clone()
	{
		return (JournalMessageItem) super.clone();
	}

	@Override
	public String toString()
	{
		return "JournalMessageItem{" +
				super.toString() +
				", image=" + (image != null ? ("yes, " + ByteUnitUtils.fromBytes(image.length)) : "no") +
				", read=" + read +
				'}';
	}
}

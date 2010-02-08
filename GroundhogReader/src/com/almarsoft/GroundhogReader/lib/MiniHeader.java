package com.almarsoft.GroundhogReader.lib;

import org.apache.commons.net.nntp.Article;

public class MiniHeader {
	public Article article;
	public int depth;
	public String replyto;
	
	public MiniHeader(Article art, int dep, String rep) {
		article = art;
		depth = dep;
		replyto = rep;
	}

}

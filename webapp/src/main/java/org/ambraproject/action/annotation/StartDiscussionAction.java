/* $HeadURL:: http://ambraproject.org/svn/ambra/ambra/branches/create-permissions/webapp#$
 * $Id:GetAnnotationAction.java 722 2006-10-02 16:42:45Z viru $
 *
 * Copyright (c) 2006-2010 by Public Library of Science
 * http://plos.org
 * http://ambraproject.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ambraproject.action.annotation;

import org.ambraproject.action.BaseActionSupport;
import org.ambraproject.service.article.ArticleService;
import org.ambraproject.views.article.ArticleInfo;
import org.springframework.beans.factory.annotation.Required;

/**
 * Basic action for the start discussion page. just fetches up article doi and title.
 * @author Alex Kudlick 4/12/12
 */
public class StartDiscussionAction extends BaseActionSupport {

  private ArticleService articleService;
  private ArticleInfo articleInfo;
  private String articleURI;


  @Override
  public String execute() throws Exception {
    articleInfo = articleService.getBasicArticleView(articleURI);
    return SUCCESS;
  }

  @Required
  public void setArticleService(ArticleService articleService) {
    this.articleService = articleService;
  }

  public void setArticleURI(String articleURI) {
    this.articleURI = articleURI;
  }

  public ArticleInfo getArticleInfo() {
    return articleInfo;
  }
}
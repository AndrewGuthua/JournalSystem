/*
 * $HeadURL$
 * $Id$
 *
 * Copyright (c) 2006-2012 by Public Library of Science
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

$.fn.comments = function () {

  /**
   * Duration of JQuery animations to show and hide page elements, in milliseconds.
   * @type {Number}
   */
  var DURATION = 500;

  /**
   * Return a reference to a JQuery page element.
   * @param elementType  the prefix of the element ID
   * @param replyId  the ID of the reply to which the element to get belongs, or null if the page has only one reply
   * @return {*} the element
   */
  function getReplyElement(elementType, replyId) {
    return (replyId == null)
      ? $('#' + elementType)
      : $('#' + elementType + '-' + replyId);
  }

  /**
   * Clear the box beneath a reply (whichever one is showing, if any).
   * @param replyId  the ID of the reply whose box should be cleared
   */
  this.clearReply = function (replyId) {
    ["report", "respond"].forEach(function (replyType) {
      getReplyElement(replyType, replyId).hide("blind", {direction:"vertical"}, DURATION)
    });
  }

  /**
   * Hide a box beneath a reply, then show one.
   * @param from  the type of box to hide
   * @param to  the type of box to show
   * @param replyId  the ID of the reply to which the boxes belong
   */
  function switchReplyBox(from, to, replyId) {
    getReplyElement(from, replyId).hide();
    getReplyElement(to, replyId).show("blind", DURATION);
  }

  /**
   * Show the "report a concern" box beneath a reply, clearing the response box first if necessary.
   * @param replyId  the ID of the reply where the box should be shown
   */
  this.showReportBox = function (replyId) {
    switchReplyBox("respond", "report", replyId);
  }

  /**
   * Show the "respond to this posting" box beneath a reply, clearing the report box first if necessary.
   * @param replyId  the ID of the reply where the box should be shown
   */
  this.showRepondBox = function (replyId) {
    switchReplyBox("report", "respond", replyId);
  }

  /**
   * Submit a top-level response to an article and show the result. Talks to the server over Ajax.
   * @param articleDoi the DOI of the article to which the user is responding
   * @param submitUrl  the URL to send the Ajax request to
   * @param forwardUrl  the URL to which to forward the user after the server accepts the response
   * @param forwardParam  the parameter of forwardUrl that should be the new response's ID
   */
  this.submitDiscussion = function (articleDoi, submitUrl, forwardUrl, forwardParam) {
    var commentData = getCommentData(null);
    commentData.target = articleDoi;
    var submittedCallback = function (data) {
      window.location = forwardUrl + '?' + forwardParam + '=' + data.annotationId;
    }
    sendComment(commentData, null, submitUrl, submittedCallback);
  }

  /**
   * Submit the response data from a reply's response box and show the result. Talks to the server over Ajax.
   * @param parentId  the ID of the existing reply, to which the user is responding
   * @param submitUrl  the URL to send the Ajax request to
   */
  this.submitResponse = function (parentId, submitUrl, getUrl) {
    var commentData = getCommentData(parentId);
    commentData.inReplyTo = parentId;

    var submittedCallback = function (data) {
      // Make a second Ajax request to get the new comment (we need its back-end representation)
      $.ajax(getUrl, {
        dataType:"json",
        data:{annotationId:data.replyId},
        dataFilter:function (data, type) {
          return data.replace(/(^\s*\/\*\s*)|(\s*\*\/\s*$)/g, '');
        },
        success:function (data, textStatus, jqXHR) {
          // Got the new comment; now add the content to the page
          putComment(parentId, data.annotation);
        },
        error:function (jqXHR, textStatus, errorThrown) {
          alert(textStatus + '\n' + errorThrown);
        },
        complete:function (jqXHR, textStatus) {
        }
      });
    }

    sendComment(commentData, parentId, submitUrl, submittedCallback);
  }

  /**
   * Send a comment to the server. The comment may be a top-level article comment, or a response to another response.
   *
   * @param commentData  the comment's content, as an object that can be sent to the server
   * @param parentId  the ID of the parent reply, or null if the page doesn't show other replies
   * @param submitUrl  the URL to send the Ajax request to
   * @param submittedCallback  a function to call after the comment has been submitted without errors
   */
  function sendComment(commentData, parentId, submitUrl, submittedCallback) {
    var errorMsg = getReplyElement("responseSubmitMsg", parentId);
    errorMsg.hide(); // in case it was already shown from a previous attempt

    $.ajax(submitUrl, {
        dataType:"json",
        data:commentData,
        dataFilter:function (data, type) {
          return data.replace(/(^\s*\/\*\s*)|(\s*\*\/\s*$)/g, '');
        },
        success:function (data, textStatus, jqXHR) {
          var errors = Array();
          for (var errorKey in data.fieldErrors) {
            errors.push(data.fieldErrors[errorKey]);
          }
          if (errors.length > 0) {
            errorMsg.html(errors.join('<br/>'));
            errorMsg.show("blind", DURATION);
          } else {
            submittedCallback(data);
          }
        },
        error:function (jqXHR, textStatus, errorThrown) {
          alert(textStatus + '\n' + errorThrown);
        },
        complete:function (jqXHR, textStatus) {
        }
      }
    );
  }

  /**
   * Add a comment in its proper place in its thread.
   * @param parentId  the ID of the comment's parent (defines where to put the new comment)
   * @param newResponse  data for the new response (currently from AnnotationView; TODO finalize contract)
   */
  function putComment(parentId, commentData) {
    // TODO Finish implementing
    var html = [
        '<div class="response">',
        '  <div class="info">',
        '    <h3>', commentData.title, '</h3>',
        '    <h4>',
        '      <a href="{showUserURL}" class="user icon">{reply.creatorDisplayName}</a>',
        '      replied to',
        '      <a href="{authorURL}" class="user icon">{replyToAuthorName}</a>',
        '      on <strong>{reply.created?string("dd MMM yyyy </strong>at<strong> HH:mm zzz")}</strong>',
        '    </h4>',
        '      <div class="arrow"></div>',
        '  </div>',
        '  <div class="response_content">',
        commentData.body,
        '      <div class="competing_interests">',
//        commentData.isCompetingInterest ?
        '  <strong>Competing interests declared:</strong> ' + commentData.competingInterestStatement,
//          : '  <strong>No competing interests declared.</strong>',
        '      </div>',
        '  </div>',
        '</div>'
      ]
      ;
    getReplyElement("replies_to", parentId).append($(html.join(' ')));
  }

  /**
   * Pull the input for a submitted comment from the page.
   * @param parentId  the ID of the existing reply, to which the user is responding
   * @return {Object}  the response data, formatted to be sent over Ajax
   */
  function getCommentData(parentId) {
    var data = {
      commentTitle:getReplyElement("comment_title", parentId).val(),
      comment:getReplyElement("comment", parentId).val()
    };

    if (getReplyElement("no_competing", parentId).attr("checked")) {
      data.isCompetingInterest = false;
    } else {
      data.isCompetingInterest = true;
      data.ciStatement = getReplyElement("competing_interests", parentId).val();
    }

    return data;
  }

}

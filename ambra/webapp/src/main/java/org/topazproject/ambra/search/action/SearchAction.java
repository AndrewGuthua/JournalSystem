/* $HeadURL::                                                                            $
 * $Id$
 *
 * Copyright (c) 2006-2008 by Topaz, Inc.
 * http://topazproject.org
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
package org.topazproject.ambra.search.action;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import java.net.URI;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.queryParser.QueryParser;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Required;

import org.topazproject.ambra.action.BaseSessionAwareActionSupport;
import org.topazproject.ambra.article.service.BrowseService;
import org.topazproject.ambra.search.SearchResultPage;
import org.topazproject.ambra.search.service.SearchHit;
import org.topazproject.ambra.search.service.SearchService;
import org.topazproject.ambra.models.Journal;
import org.topazproject.ambra.journal.JournalService;

/**
 * Search Action class to search for simple or advanced search.
 *
 * @author Viru
 */
@SuppressWarnings("serial")
public class SearchAction extends BaseSessionAwareActionSupport {
  private static final Logger log  = LoggerFactory.getLogger(SearchAction.class);
  private static final DateFormat luceneDateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private static final String SEARCH_PAGE_SIZE = "ambra.services.search.pageSize";

  private SearchService   searchService;
  private BrowseService   browseService;
  private JournalService  journalService;

  private String query = "";
  private int    startPage = 0;
  private int    pageSize = 0;

  private Collection<SearchHit> searchResults;
  private int                   totalNoOfResults;
  // empty map for non-null safety
  private Map<String, List<URI>> categoryInfos = new HashMap<String, List<URI>>();

  // Flag telling this action whether or not the search should be executed.
  private String   noSearchFlag;

  private String[]      creator;
  private String        authorNameOp = "";
  private String        textSearchAll = "";
  private String        textSearchExactPhrase = "";
  private String        textSearchAtLeastOne = "";
  private String        textSearchWithout = "";
  private String        textSearchOption = "";
  private String        dateTypeSelect;
  private String        startDate;
  private String        endDate;
  private String        subjectCatOpt;
  private String[]      limitToCategory;
  private Set<Journal>  journals;  //  Creates list of Journals for display
  private String        journalOpt;  //  Whether to limit a search to Journals in limitToJournal
  // Only search these Journals. If no journals were selected, default to the current journal.
  private String[]      limitToJournal;

  /**
   * @return return simple search result
   */
  @Transactional(readOnly = true)
  public String executeSimpleSearch() {
    setDefaultValues(); // journalOpt and limitToJournal are NOT set in form in global_header.ftl
    // the simple search text field correlates to advanced search's "for all the words" field
    this.textSearchAll = query;
    return executeSearch(query);
  }

  /**
   * @return return simple search result
   */
  @Transactional(readOnly = true)
  public String executeAdvancedSearch() {
    setDefaultValues();
    if(doSearch()) {
      query = buildAdvancedQuery();
      return executeSearch(query);
    }

    return INPUT;
  }

  /**
   * Set values used for processing and/or display by both the Simple and Advanced searches.
   */
  private void setDefaultValues() {
    categoryInfos = browseService.getArticlesByCategory();
    journals = getJournalsForCrossJournalSearch();
    if (limitToJournal == null || limitToJournal.length < 1) {
      String currentJournal = getCurrentJournal();
      if (currentJournal != null) {
        limitToJournal = new String[] {currentJournal};
        if (journalOpt == null || (! "all".equals(journalOpt) && ! "some".equals(journalOpt))) {
          journalOpt = "some"; // Since one default journal set, search only that one journal
        }
      } else if (journalOpt == null || (! "all".equals(journalOpt) && ! "some".equals(journalOpt))){
          journalOpt = "all"; // If no default could be set journal, then try to search all journals
        }
    }
  }

  private String executeSearch(final String queryString) {
    if (pageSize == 0)
      pageSize = configuration.getInt(SEARCH_PAGE_SIZE, 10);

    if (StringUtils.isBlank(queryString) || queryString.equals("Search articles...")) {
      
      addFieldError("query","Please enter a search query.");
      query = "";
      textSearchAll = "";
      
      categoryInfos = browseService.getArticlesByCategory();
      return INPUT;
    } else {
      //If user types in date into simple search
      //Stop them here to avoid mulgara bug
      if(queryString.toLowerCase().indexOf("date:") >= 0) {
        addFieldError("query","Sorry, but you can not currently search on dates.");
        query = "";
        textSearchAll = "";

        categoryInfos = browseService.getArticlesByCategory();
        return INPUT;
      }

      try {
        SearchResultPage results;
        if ("all".equals(journalOpt)) {
          journals = getJournalsForCrossJournalSearch();
          String[] journalNames = new String[journals.size()];
          int counter = 0;
          for (Journal journal : journals) {
            journalNames[counter++] = journal.getKey();
          }
          results = searchService.find(
              queryString, journalNames, startPage, pageSize, getCurrentUser());
        } else {
          results = searchService.find(
              queryString, limitToJournal, startPage, pageSize, getCurrentUser());
        }
        totalNoOfResults = results.getTotalNoOfResults();
        searchResults    = results.getHits();

        int totPages = (totalNoOfResults + pageSize - 1) / pageSize;
        startPage = Math.max(0, Math.min(startPage, totPages - 1));
      } catch (org.apache.lucene.queryParser.ParseException pe) {
        addActionError("Search Query Bad");

        query = "";
        textSearchAll = "";

        log.warn("Search failed with error with query string: " + queryString, pe);

        //Implemented this as a stop gap.  Search functionality needs to be refactored
        return "badquery";
      } catch (Exception e) {
        addActionError("Search failed");
        log.error("Search failed with error with query string: " + queryString, e);
        return ERROR;
      }

      return SUCCESS;
    }
  }

  /**
   * Get all journals that are to be included in cross-journal searches, ordered by journal title.
   * By default, each journal is included, unless the property
   * <code>ambra.virtualJournals.JOURNAL_KEY.isIncludeInCrossJournalSearch</code> is set to
   * <code>false</code> in the configuration for a given journal.
   *
   * @return journals that are to be included in cross-journal searches
   */
  private Set<Journal> getJournalsForCrossJournalSearch() {
    TreeSet<Journal> orderedByName = new TreeSet<Journal>(
        new Comparator<Journal>() {
          public int compare(Journal journal1, Journal journal2) {
            return journal1.getDublinCore().getTitle().compareTo(
                   journal2.getDublinCore().getTitle());
          }
        }
    );

    orderedByName.addAll(journalService.getAllJournals());
    Iterator journalIterator = orderedByName.iterator();
    while (journalIterator.hasNext()) {
      Journal journal = (Journal)journalIterator.next();
      if ( ! configuration.getBoolean("ambra.virtualJournals." + journal.getKey()
          + ".isIncludeInCrossJournalSearch", true))
        journalIterator.remove();
    }
    return orderedByName;
  }

  private String buildAdvancedQuery() {
    final Collection<String> fields = new ArrayList<String>();

    // Build Search terms for Authors if specified
    if ((creator != null) && (creator.length > 0) && (StringUtils.isNotBlank(creator[0]))) {
      StringBuilder buf = new StringBuilder("(creator:(");
      boolean allAuthors = false;
      if ("all".equals(authorNameOp)) {
        allAuthors = true;
      }
      for (int i=0; i<creator.length; i++) {
        String creatorName = creator[i];
        if (StringUtils.isNotBlank(creatorName)) {
          buf.append("\"").append(escape(creatorName)).append("\"");
        }
        if ((i < creator.length-1) && (StringUtils.isNotBlank(creator[i+1]))) {
          if (allAuthors) {
            buf.append(" AND ");
          } else {
            buf.append(" OR ");
          }
        }
      }
      buf.append(" ))");
      fields.add(buf.toString());
    }

    // Build Search Article Text section of advanced search.
    String textSearchField = null;
    if (StringUtils.isNotBlank(textSearchOption)) {
      if ("abstract".equals(textSearchOption)) {
        textSearchField = "description:";
      }
      else if ("refs".equals(textSearchOption)) {
        textSearchField = "reference:";
      }
      else if ("title".equals(textSearchOption)) {
        textSearchField = "title:";
      }
    }
    // All words field should be in parenthesis with AND between each word.
    if (StringUtils.isNotBlank(textSearchAll)) {
      String[] words = StringUtils.split(textSearchAll);
      StringBuilder buf = new StringBuilder();
      if (textSearchField != null) {
        buf.append(textSearchField);
      }
      buf.append("( ");
      for (int i=0; i<words.length; i++) {
        buf.append(escape(words[i]));
        if (i < words.length-1) {
          buf.append(" AND ");
        }
      }
      buf.append(" )");
      fields.add(buf.toString());
    }

    // Exact phrase should be placed in quotes
    if (StringUtils.isNotBlank(textSearchExactPhrase)) {
      StringBuilder buf = new StringBuilder();
      if (textSearchField != null) {
        buf.append(textSearchField);
      }
      buf.append("(\"").append(escape(textSearchExactPhrase)).append("\")");
      fields.add(buf.toString());
    }

    // At least one of should be placed in parenthesis separated by spaced (OR'ed)
    if (StringUtils.isNotBlank(textSearchAtLeastOne)) {
      StringBuilder buf = new StringBuilder();
      if (textSearchField != null) {
        buf.append(textSearchField);
      }
      buf.append("( ").append(escape(textSearchAtLeastOne)).append(" )");
      fields.add(buf.toString());
    }

    /*
     * Without the words - in parenthesis with NOT operator and AND'ed together
     * E.g. (-"the" AND -"fat" AND -"cat")
     */
    if (StringUtils.isNotBlank(textSearchWithout)) {
      /*
       * TODO - we might want to allow the entry of phrases to omit (entered using quotes?)
       *      - in which case we have to be smarter about how we split...
       */
      String[] words = StringUtils.split(textSearchWithout);
      StringBuilder buf = new StringBuilder();
      if (textSearchField != null) {
        buf.append(textSearchField);
      }
      buf.append("( ");
      for (int i=0; i<words.length; i++) {
        buf.append("-\"").append(escape(words[i])).append("\"");
        if (i < words.length-1) {
          buf.append(" AND ");
        }
      }
      buf.append(" )");
      fields.add(buf.toString());
    }

    if (StringUtils.isNotBlank(dateTypeSelect)) {
      String startDateStr, endDateStr;

      if ("range".equals(dateTypeSelect)) {
        synchronized(luceneDateFormat) {
          //  Validate that the incoming date values can be parsed as Dates.
          Date startDateAsDate = null;
          Date endDateAsDate = null;
          try {
            startDateAsDate = luceneDateFormat.parse(startDate);
          } catch (ParseException pe) {
            log.warn("This search start date could not be parsed: " + startDate);
          }
          try {
            endDateAsDate = luceneDateFormat.parse(endDate);
          } catch (ParseException pe) {
            log.warn("This search end date could not be parsed: " + endDate);
          }
          startDateStr = luceneDateFormat.format(startDateAsDate);
          endDateStr = luceneDateFormat.format(endDateAsDate);
        }
      } else {
        Calendar cal = new GregorianCalendar();
        if ("week".equals(dateTypeSelect)) {
          cal.add(Calendar.DATE, -7);
        }
        if ("month".equals(dateTypeSelect)) {
          cal.add(Calendar.MONTH, -1);
        }
        if ("3months".equals(dateTypeSelect)) {
          cal.add(Calendar.MONTH, -3);
        }
        if ("6months".equals(dateTypeSelect)) {
          cal.add(Calendar.MONTH, -6);
        }

        synchronized(luceneDateFormat) {
          endDateStr = luceneDateFormat.format(new Date());
          startDateStr = luceneDateFormat.format(cal.getTime());
        }
      }

      StringBuilder buf = new StringBuilder("date:[");
      buf.append(startDateStr).append(" TO ").append(endDateStr).append("]");
      fields.add(buf.toString());
    }

    if ("some".equals(subjectCatOpt)) {
      if ((limitToCategory != null) && (limitToCategory.length > 0)) {
        StringBuilder buf = new StringBuilder("subject:( " );
        for (String aLimitToCategory : limitToCategory) {
          buf.append("\"").append(escape(aLimitToCategory)).append("\" ");
        }
        buf.append(")");
        fields.add(buf.toString());
      }
    }

    String advSearchQueryStr = StringUtils.join(fields.iterator(), " AND ");
    if (log.isDebugEnabled()) {
      log.debug("Generated advanced search query: " + advSearchQueryStr);
    }
    return StringUtils.join(fields.iterator(), " AND ");
  }

  /**
   * Static helper method to escape special characters in a Lucene search string with a \
   */
  protected static String escape(String in) {
    return QueryParser.escape(in);
  }

  protected static boolean isDigit(String str) {
    if (str == null) return false;
    for (int i=0; i < str.length(); i++) {
      if (!(Character.isDigit(str.charAt(i)))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Set the simple query
   * @param query query
   */
  public void setQuery(final String query) {
    this.query = query;
  }

  /**
   * Set the startPage
   * @param startPage startPage
   */
  public void setStartPage(final int startPage) {
    this.startPage = startPage;
  }

  /**
   * Set the pageSize
   * @param pageSize pageSize
   */
  public void setPageSize(final int pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * Set the searchService
   * @param searchService searchService
   */
  @Required
  public void setSearchService(final SearchService searchService) {
    this.searchService = searchService;
  }

  /**
   * Spring injected BrowseService
   * @param browseService searchService
   */
  @Required
  public void setBrowseService(final BrowseService browseService) {
    this.browseService = browseService;
  }

  /**
   * Spring injected JournalService
   * @param journalService journalService
   */
  @Required
  public void setJournalService(JournalService journalService) {
    this.journalService = journalService;
  }

  /**
   * @return the search results.
   */
  public Collection<SearchHit> getSearchResults() {
    return searchResults;
  }

  /**
   * Getter for property 'pageSize'.
   * @return Value for property 'pageSize'.
   */
  public int getPageSize() {
    return pageSize;
  }

  /**
   * Getter for property 'query'.
   * @return Value for property 'query'.
   */
  public String getQuery() {
    return query;
  }

  /**
   * Getter for property 'startPage'.
   * @return Value for property 'startPage'.
   */
  public int getStartPage() {
    return startPage;
  }

  /**
   * Getter for property 'totalNoOfResults'.
   * @return Value for property 'totalNoOfResults'.
   */
  public int getTotalNoOfResults() {
    return totalNoOfResults;
  }

  public String getTextSearchAll() {
    return textSearchAll;
  }

  public void setTextSearchAll(String textSearchAll) {
    this.textSearchAll = textSearchAll;
  }

  public String getTextSearchExactPhrase() {
    return textSearchExactPhrase;
  }

  public void setTextSearchExactPhrase(String textSearchExactPhrase) {
    this.textSearchExactPhrase = textSearchExactPhrase;
  }

  public String getTextSearchAtLeastOne() {
    return textSearchAtLeastOne;
  }

  public void setTextSearchAtLeastOne(String textSearchAtLeastOne) {
    this.textSearchAtLeastOne = textSearchAtLeastOne;
  }

  public String getTextSearchWithout() {
    return textSearchWithout;
  }

  public void setTextSearchWithout(String textSearchWithout) {
    this.textSearchWithout = textSearchWithout;
  }

  public String getTextSearchOption() {
    return textSearchOption;
  }

  public void setTextSearchOption(String textSearchOption) {
    this.textSearchOption = textSearchOption;
  }

  public String getDateTypeSelect() {
    return dateTypeSelect;
  }

  public void setDateTypeSelect(String dateTypeSelect) {
    // Turning off date setters to avoid Mulgara bug
    //this.dateTypeSelect = dateTypeSelect;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String startDate) {
    // Turning off date setters to avoid Mulgara bug
    //this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(String endDate) {
    // Turning off date setters to avoid Mulgara bug
    // this.endDate = endDate;
  }

  public String getSubjectCatOpt() {
    return subjectCatOpt;
  }

  public void setSubjectCatOpt(String subjectCatOpt) {
    this.subjectCatOpt = subjectCatOpt;
  }

  public Map<String, List<URI>> getCategoryInfos() {
    return categoryInfos;
  }

  public String[] getCreator() {
    return creator;
  }

  /**
   * @return The {@link #creator} array as a single comma delimited String.
   */
  public String getCreatorStr() {
    if(creator == null) return "";
    StringBuilder sb = new StringBuilder();
    for(String auth : creator) {
      sb.append(',');
      sb.append(auth.trim());
    }
    return sb.toString().substring(1);
  }

  /**
   * Converts a String array whose first element may be a comma delimited String
   * into a new String array whose elements are the split comma delimited elements.
   * @param arr String array that may contain one or more elements having a comma delimited String.
   * @return Rectified String[] array or
   *         <code>null</code> when the given String array is <code>null</code>.
   */
  private String[] rectify(String[] arr) {
    if(arr != null && arr.length == 1 && arr[0].length() > 0) {
      arr = arr[0].split(",");
      for(int i = 0; i < arr.length; i++) {
        arr[i] = arr[i] == null ? null : arr[i].trim();
      }
    }
    return arr;
  }

  public void setCreator(String[] creator) {
    this.creator = rectify(creator);
  }

  public void setJournalOpt(String journalOpt) {
    this.journalOpt = journalOpt;
  }

  public String getJournalOpt() {
    return journalOpt;
  }

  public void setLimitToJournal(String[] limitToJournal) {
    this.limitToJournal = rectify(limitToJournal);
  }

  public String[] getLimitToJournal() {
    return limitToJournal;
  }

  public Set<Journal> getJournals() {
    return journals;
  }

  public String[] getLimitToCategory() {
    return limitToCategory;
  }

  public void setLimitToCategory(String[] limitToCategory) {
    this.limitToCategory = rectify(limitToCategory);
  }

  /**
   * @return the authorNameOp
   */
  public String getAuthorNameOp() {
    return authorNameOp;
  }

  /**
   * @param authorNameOp the authorNameOp to set
   */
  public void setAuthorNameOp(String authorNameOp) {
    this.authorNameOp = authorNameOp;
  }

  /**
   * @return the noSearchFlag
   */
  public String getNoSearchFlag() {
    return noSearchFlag;
  }

  /**
   * @param noSearchFlag the noSearchFlag to set
   */
  public void setNoSearchFlag(String noSearchFlag) {
    this.noSearchFlag = noSearchFlag;
  }

  private boolean doSearch() {
    return noSearchFlag == null;
  }
}
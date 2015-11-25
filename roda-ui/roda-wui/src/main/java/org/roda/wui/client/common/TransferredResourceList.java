/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.sort.SortParameter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.IndexResult;
import org.roda.core.data.v2.TransferredResource;
import org.roda.wui.client.browse.BrowserService;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.tools.Humanize;
import org.roda.wui.common.client.widgets.AsyncTableCell;

import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.DateCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.DefaultSelectionEventManager;
import com.google.gwt.view.client.ProvidesKey;

/**
 * 
 * @author Luis Faria <lfaria@keep.pt>
 *
 */
public class TransferredResourceList extends AsyncTableCell<TransferredResource> {

  private static final int PAGE_SIZE = 20;

  // private static IngestListConstants constants =
  // GWT.create(IngestListConstants.class);

  private final ClientLogger logger = new ClientLogger(getClass().getName());

  private Column<TransferredResource, Boolean> selectColumn;
  private final Set<TransferredResource> selected = new HashSet<TransferredResource>();
  private final List<CheckboxSelectionListener> listeners = new ArrayList<TransferredResourceList.CheckboxSelectionListener>();

  private Column<TransferredResource, SafeHtml> isFileColumn;
  // private TextColumn<TransferredResource> idColumn;
  private TextColumn<TransferredResource> nameColumn;
  private TextColumn<TransferredResource> sizeColumn;
  private Column<TransferredResource, Date> creationDateColumn;
  private TextColumn<TransferredResource> ownerColumn;

  public TransferredResourceList() {
    this(null, null, null);
  }

  public TransferredResourceList(Filter filter, Facets facets, String summary) {
    super(filter, facets, summary);
  }

  @Override
  protected void configureDisplay(final CellTable<TransferredResource> display) {

    selectColumn = new Column<TransferredResource, Boolean>(new CheckboxCell(true, false)) {
      @Override
      public Boolean getValue(TransferredResource resource) {
        return getSelected().contains(resource);
      }
    };

    selectColumn.setFieldUpdater(new FieldUpdater<TransferredResource, Boolean>() {
      @Override
      public void update(int index, TransferredResource resource, Boolean isSelected) {
        if (isSelected) {
          getSelected().add(resource);
        } else {
          getSelected().remove(resource);
        }

        // update header
        display.redrawHeaders();
        fireOnCheckboxSelectionChanged();
      }
    });

    isFileColumn = new Column<TransferredResource, SafeHtml>(new SafeHtmlCell()) {
      @Override
      public SafeHtml getValue(TransferredResource r) {
        SafeHtml ret;
        if (r == null) {
          logger.error("Trying to display a NULL item");
          ret = null;
        } else if (r.isFile()) {
          ret = SafeHtmlUtils.fromSafeConstant("<i class='fa fa-file-o'></i>");
        } else {
          ret = SafeHtmlUtils.fromSafeConstant("<i class='fa fa-folder-o'></i>");
        }
        return ret;
      }
    };

    // idColumn = new TextColumn<TransferredResource>() {
    //
    // @Override
    // public String getValue(TransferredResource r) {
    // return r != null ? r.getId() : null;
    // }
    // };

    nameColumn = new TextColumn<TransferredResource>() {

      @Override
      public String getValue(TransferredResource r) {
        return r != null ? r.getName() : null;
      }
    };

    sizeColumn = new TextColumn<TransferredResource>() {

      @Override
      public String getValue(TransferredResource r) {
        return r != null ? Humanize.readableFileSize(r.getSize()) : null;
      }
    };

    creationDateColumn = new Column<TransferredResource, Date>(
      new DateCell(DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss"))) {
      @Override
      public Date getValue(TransferredResource r) {
        return r != null ? r.getCreationDate() : null;
      }
    };

    ownerColumn = new TextColumn<TransferredResource>() {

      @Override
      public String getValue(TransferredResource r) {
        return r != null ? r.getOwner() : null;
      }
    };

    isFileColumn.setSortable(true);
    // idColumn.setSortable(true);
    nameColumn.setSortable(true);
    sizeColumn.setSortable(true);
    creationDateColumn.setSortable(true);
    ownerColumn.setSortable(true);

    Header<Boolean> selectHeader = new Header<Boolean>(new CheckboxCell(true, true)) {

      @Override
      public Boolean getValue() {
        Boolean ret;

        if (selected.isEmpty()) {
          ret = false;
        } else if (selected.containsAll(getVisibleItems())) {
          ret = true;
        } else {
          // some are selected
          ret = false;
        }

        return ret;
      }
    };

    selectHeader.setUpdater(new ValueUpdater<Boolean>() {

      @Override
      public void update(Boolean value) {
        if (value) {
          selected.addAll(getVisibleItems());

        } else {
          selected.clear();
        }
        redraw();
        fireOnCheckboxSelectionChanged();
      }
    });
    
    addValueChangeHandler(new ValueChangeHandler<IndexResult<TransferredResource>>() {

      @Override
      public void onValueChange(ValueChangeEvent<IndexResult<TransferredResource>> event) {
        selected.clear();
        fireOnCheckboxSelectionChanged();
      }
    });

    // TODO externalize strings into constants
    display.addColumn(selectColumn, selectHeader);
    display.addColumn(isFileColumn, SafeHtmlUtils.fromSafeConstant("<i class='fa fa-files-o'></i>"));
    // display.addColumn(idColumn, "Id");
    display.addColumn(nameColumn, "Name");
    display.addColumn(sizeColumn, "Size");
    display.addColumn(creationDateColumn, "Date created");
    display.addColumn(ownerColumn, "Producer");

    // display.setAutoHeaderRefreshDisabled(true);
    Label emptyInfo = new Label("No items to display");
    display.setEmptyTableWidget(emptyInfo);
    display.setColumnWidth(nameColumn, "100%");

    addStyleName("my-list-transferredResource");
    emptyInfo.addStyleName("my-list-transferredResource-empty-info");

    // idColumn.setCellStyleNames("nowrap");
    sizeColumn.setCellStyleNames("nowrap my-collections-table-cell-alignright");
    creationDateColumn.setCellStyleNames("nowrap my-collections-table-cell-alignright");
    ownerColumn.setCellStyleNames("nowrap");
  }

  @Override
  protected void getData(int start, int length, ColumnSortList columnSortList,
    AsyncCallback<IndexResult<TransferredResource>> callback) {

    Filter filter = getFilter();

    // calculate sorter
    Sorter sorter = new Sorter();
    for (int i = 0; i < columnSortList.size(); i++) {
      ColumnSortInfo columnSortInfo = columnSortList.get(i);
      String sortParameterKey;
      // if (columnSortInfo.getColumn().equals(idColumn)) {
      // sortParameterKey = RodaConstants.TRANSFERRED_RESOURCE_ID;
      // } else
      if (columnSortInfo.getColumn().equals(isFileColumn)) {
        sortParameterKey = RodaConstants.TRANSFERRED_RESOURCE_ISFILE;
      } else if (columnSortInfo.getColumn().equals(nameColumn)) {
        sortParameterKey = RodaConstants.TRANSFERRED_RESOURCE_NAME;
      } else if (columnSortInfo.getColumn().equals(sizeColumn)) {
        sortParameterKey = RodaConstants.TRANSFERRED_RESOURCE_SIZE;
      } else if (columnSortInfo.getColumn().equals(creationDateColumn)) {
        sortParameterKey = RodaConstants.TRANSFERRED_RESOURCE_DATE;
      } else if (columnSortInfo.getColumn().equals(ownerColumn)) {
        sortParameterKey = RodaConstants.TRANSFERRED_RESOURCE_OWNER;
      } else {
        sortParameterKey = null;
      }

      if (sortParameterKey != null) {
        sorter.add(new SortParameter(sortParameterKey, !columnSortInfo.isAscending()));
      } else {
        logger.warn("Selecting a sorter that is not mapped");
      }
    }

    // define sublist
    Sublist sublist = new Sublist(start, length);

    BrowserService.Util.getInstance().findTransferredResources(filter, sorter, sublist, getFacets(), callback);

  }

  @Override
  protected ProvidesKey<TransferredResource> getKeyProvider() {
    return new ProvidesKey<TransferredResource>() {

      @Override
      public Object getKey(TransferredResource item) {
        return item.getId();
      }
    };
  }

  @Override
  protected int getInitialPageSize() {
    return PAGE_SIZE;
  }

  public Set<TransferredResource> getSelected() {
    return selected;
  }

  public void setSelected(Set<TransferredResource> newSelected) {
    selected.clear();
    selected.addAll(newSelected);
    redraw();
    fireOnCheckboxSelectionChanged();
  }

  @Override
  protected CellPreviewEvent.Handler<TransferredResource> getSelectionEventManager() {
    return DefaultSelectionEventManager.<TransferredResource> createBlacklistManager(0);
  }

  // LISTENER

  public interface CheckboxSelectionListener {
    public void onSelectionChange(Set<TransferredResource> selected);
  }

  public void addCheckboxSelectionListener(CheckboxSelectionListener listener) {
    listeners.add(listener);
  }

  public void removeCheckboxSelectionListener(CheckboxSelectionListener listener) {
    listeners.remove(listener);
  }

  public void fireOnCheckboxSelectionChanged() {
    for (CheckboxSelectionListener listener : listeners) {
      listener.onSelectionChange(getSelected());
    }
  }

}

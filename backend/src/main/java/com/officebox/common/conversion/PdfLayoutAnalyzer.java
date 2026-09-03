package com.officebox.common.conversion;

import com.officebox.common.conversion.model.BoundingBox;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.TableBlock;
import com.officebox.common.conversion.model.TextBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Turns low-level PDF blocks into conservative editable structures. */
@Component
public class PdfLayoutAnalyzer {
  public List<PageBlock> analyze(List<PageBlock> input) {
    List<PageBlock> blocks = new ArrayList<>(input);
    List<TextBlock> text = blocks.stream().filter(TextBlock.class::isInstance)
        .map(TextBlock.class::cast).sorted(topDown()).toList();
    TableMatch table = detectTable(text);
    if (table == null) return sorted(blocks);
    Set<TextBlock> consumed = new HashSet<>(table.cells());
    List<PageBlock> result = new ArrayList<>();
    for (PageBlock block : blocks) if (!(block instanceof TextBlock tb) || !consumed.contains(tb)) result.add(block);
    result.add(table.table());
    return sorted(result);
  }

  private TableMatch detectTable(List<TextBlock> text) {
    if (text.size() < 6) return null;
    List<List<TextBlock>> rows = new ArrayList<>();
    for (TextBlock block : text) {
      List<TextBlock> row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
      if (row == null || Math.abs(row.getFirst().bounds().y() - block.bounds().y()) > Math.max(4, block.bounds().height() * .65)) {
        row = new ArrayList<>(); rows.add(row);
      }
      row.add(block);
    }
    rows.removeIf(r -> r.size() < 2);
    if (rows.size() < 3) return null;
    rows = rows.stream().map(r -> r.stream().sorted(Comparator.comparingDouble(b -> b.bounds().x())).toList()).toList();
    int columns = rows.stream().mapToInt(List::size).min().orElse(0);
    if (columns < 2) return null;
    long strongRows = rows.stream().filter(r -> r.size() == columns || r.size() == columns + 1).count();
    if (strongRows < Math.max(3, rows.size() * .65)) return null;
    double xSpread = columnSpread(rows, columns);
    if (xSpread < 12) return null;
    List<List<TextBlock>> normalized = rows.stream().map(r -> r.size() <= columns ? r : r.subList(0, columns)).toList();
    List<TextBlock> cells = normalized.stream().flatMap(List::stream).toList();
    double minX = cells.stream().mapToDouble(b -> b.bounds().x()).min().orElse(0);
    double minY = cells.stream().mapToDouble(b -> b.bounds().y()).min().orElse(0);
    double maxX = cells.stream().mapToDouble(b -> b.bounds().right()).max().orElse(minX);
    double maxY = cells.stream().mapToDouble(b -> b.bounds().bottom()).max().orElse(minY);
    return new TableMatch(new TableBlock(new BoundingBox(minX, minY, maxX - minX, maxY - minY), normalized), cells);
  }

  private double columnSpread(List<List<TextBlock>> rows, int columns) {
    double total = 0;
    for (int c = 1; c < columns; c++) {
      List<Double> xs = rows.stream().filter(r -> r.size() >= columns).map(r -> r.get(c).bounds().x()).toList();
      if (xs.isEmpty()) continue;
      double min = xs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
      double max = xs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
      if (max - min > 24) return 0;
      total += Math.max(12, max - min);
    }
    return total;
  }

  private static Comparator<TextBlock> topDown() {
    return Comparator.comparingDouble((TextBlock b) -> b.bounds().y()).thenComparingDouble(b -> b.bounds().x());
  }
  private static List<PageBlock> sorted(List<PageBlock> blocks) {
    return blocks.stream().sorted(Comparator.comparingDouble((PageBlock b) -> b.bounds().y()).thenComparingDouble(b -> b.bounds().x())).toList();
  }
  private record TableMatch(TableBlock table, List<TextBlock> cells) {}
}

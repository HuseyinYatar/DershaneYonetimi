package com.dershane.userservice.shared.adapter.out.persistence;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Optimistic concurrency for domain objects: they carry the {@code version} they were loaded with,
 * and saving one that is older than the stored row is rejected.
 */
public final class VersionChecks {

  private VersionChecks() {}

  public static void requireCurrent(
      Logger log, String aggregate, UUID id, Long domainVersion, Long storedVersion) {
    if (!Objects.equals(domainVersion, storedVersion)) {
      throw Rejections.reject(
          log,
          ErrorCode.CONCURRENT_MODIFICATION,
          "Save rejected: the record was modified after it was loaded",
          "aggregate",
          aggregate,
          "id",
          id,
          "loadedVersion",
          domainVersion,
          "storedVersion",
          storedVersion);
    }
  }
}

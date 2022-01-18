package com.artspace.appuser.outgoing;

import static javax.persistence.GenerationType.SEQUENCE;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@Entity
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class FailedMessage {

  @Id @GeneratedValue(strategy=SEQUENCE, generator="seq_failedmsg_id")
  private Long id;

  private String correlationId;

  @NotNull private String serializedPayload;

  private String reason;

  @Default
  @NotNull @PastOrPresent private Instant failedTime = Instant.now();

  @Default
  private boolean isProcessed = false;

  public void markAsProcessed() {
    this.isProcessed = true;
  }
}
